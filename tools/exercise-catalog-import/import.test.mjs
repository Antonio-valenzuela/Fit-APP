import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import * as exerciseImporter from './import.mjs';
import { buildCatalog, gzipCanonical, normalizeMatchText, serializeCanonical } from './import.mjs';

const fixture = JSON.parse(await readFile(new URL('./fixture.json', import.meta.url), 'utf8'));
const config = {
  normalization_rules_version: 'fitplan-exercise-import-v2', generation_date: '2026-07-15',
  builtins: [{ id: 'bench_press', name: 'Press banca', primary_muscle: 'CHEST', equipment: 'BODY_WEIGHT' }],
  muscle_map: { pectorals: 'CHEST', lats: 'BACK', triceps: 'TRICEPS', biceps: 'BICEPS' },
  body_region_map: { chest: 'CHEST', back: 'BACK' },
  equipment_map: {
    // Deliberately wrong input: the importer must still remove BODY_WEIGHT as a requirement.
    'body weight': { enum: 'BODY_WEIGHT', requirements: ['body weight'], location: 'HOME_COMPATIBLE' },
    dumbbell: { enum: 'DUMBBELL', requirements: ['dumbbell'], location: 'BOTH' },
    barbell: { enum: 'BARBELL', requirements: ['barbell', 'bench'], location: 'GYM_COMPATIBLE' }
  }
};

test('normalizes accents, case, spaces, and punctuation for exact matching', () => {
  assert.equal(normalizeMatchText('  PRÉSS---Bánca!! '), 'press banca');
});

test('creates stable namespaced IDs and safe exact duplicate references', () => {
  const { records } = buildCatalog(fixture, config);
  const exact = records.find((record) => record.external_id === 'exercises_dataset:0002');
  assert.equal(exact.name_es, null);
  assert.equal(exact.name_en, 'Press-bánca!!');
  assert.equal(exact.proposed_canonical_reference, 'bench_press');
  assert.equal(exact.import_status, 'MERGED');
});

test('does not fuse approximate name matches', () => {
  const { records } = buildCatalog(fixture, config);
  const approximate = records.find((record) => record.external_id === 'exercises_dataset:0003');
  assert.equal(approximate.proposed_canonical_reference, null);
  assert.equal(approximate.import_status, 'ACTIVE');
});

test('maps equipment and classifies home and gym compatibility', () => {
  const { records } = buildCatalog(fixture, config);
  assert.equal(records.find((r) => r.external_id.endsWith('0002')).location_compatibility, 'HOME_COMPATIBLE');
  assert.deepEqual(records.find((r) => r.external_id.endsWith('0002')).equipment_requirements, []);
  assert.deepEqual(records.find((r) => r.external_id.endsWith('0001')).equipment_requirements, ['dumbbell']);
  assert.equal(records.find((r) => r.external_id.endsWith('0001')).location_compatibility, 'BOTH');
  assert.equal(records.find((r) => r.external_id.endsWith('0003')).location_compatibility, 'GYM_COMPATIBLE');
});

test('discards incomplete records and reconciles report counts', () => {
  const result = buildCatalog(fixture, config);
  assert.equal(result.records.some((record) => record.external_id.endsWith('bad')), false);
  assert.equal(result.report.counts.read, 4);
  assert.equal(result.report.counts.valid + result.report.counts.discarded, 4);
});

test('accepts either supported language but requires at least one name and instructions', () => {
  const spanishOnly = {
    id:'0096', name:'', name_es:'Remo seguro', body_part:'back', equipment:'dumbbell',
    target:'lats', secondary_muscles:[], instructions:{es:'Rema con control.',en:''}
  };
  const englishOnly = {
    id:'0097', name:'Safe row', body_part:'back', equipment:'dumbbell',
    target:'lats', secondary_muscles:[], instructions:{es:'',en:'Row with control.'}
  };
  const missingInstructions = {...englishOnly,id:'0098',instructions:{es:'',en:''}};
  const result = buildCatalog([spanishOnly,englishOnly,missingInstructions], config);
  assert.deepEqual(result.records.map((record) => [record.external_id,record.name_es,record.name_en,record.instructions_es,record.instructions_en]), [
    ['exercises_dataset:0096','Remo seguro',null,'Rema con control.',null],
    ['exercises_dataset:0097',null,'Safe row',null,'Row with control.']
  ]);
  assert.deepEqual(result.report.discarded,[{source_id:'0098',reasons:['MISSING_INSTRUCTIONS']}]);
});

test('emits only Spanish and English text and no multimedia data', () => {
  const bytes = serializeCanonical(buildCatalog(fixture, config).records);
  const text = bytes.toString('utf8');
  assert.match(text, /Empuja con control/);
  assert.match(text, /Press safely/);
  assert.doesNotMatch(text, /image|gif|media_id|attribution|forbidden\.jpg|forbidden\.gif/i);
  assert.deepEqual(Object.keys(JSON.parse(text)[0]), [
    'external_id','name_es','name_en','instructions_es','instructions_en','primary_muscle',
    'secondary_muscles','body_region','equipment','equipment_requirements','location_compatibility',
    'import_status','proposed_canonical_reference'
  ]);
});

test('serializes deterministic byte-for-byte output', () => {
  const firstResult=buildCatalog(fixture, config), secondResult=buildCatalog([...fixture].reverse(), config);
  const first = serializeCanonical(firstResult.records);
  const second = serializeCanonical(secondResult.records);
  assert.deepEqual(first, second);
  assert.deepEqual(gzipCanonical(first), gzipCanonical(second));
  assert.deepEqual(serializeCanonical(firstResult.report), serializeCanonical(secondResult.report));
});

test('reviews unknown equipment, primary/secondary muscle, and category without hiding reasons', () => {
  const source=[{id:'0099',name:'Mystery move',body_part:'space zone',equipment:'quantum rig',target:'mystery muscle',secondary_muscles:['other mystery'],instructions:{en:'Move safely.',es:'Muévete con control.'}}];
  const result=buildCatalog(source,config);
  assert.equal(result.records[0].location_compatibility,'GYM_COMPATIBLE');
  assert.equal(result.records[0].import_status,'REVIEW');
  assert.deepEqual(result.report.unknown_equipment,['quantum rig']);
  assert.deepEqual(result.report.unknown_category,['space zone']);
  assert.deepEqual(result.report.unknown_muscle,['mystery muscle','other mystery']);
  assert.match(result.report.review[0].reasons.join('|'),/UNKNOWN_SECONDARY_MUSCLE/);
});

test('separates duplicate IDs from invalid IDs and audits exact built-in matches', () => {
  const source=[fixture[0],{...fixture[0]},{...fixture[1],id:'bad-id'}];
  const result=buildCatalog(source,config);
  assert.deepEqual(result.report.duplicate_ids,['0002']);
  assert.deepEqual(result.report.invalid_ids,['bad-id']);
  assert.deepEqual(result.report.exact_builtin_matches,[{external_id:'exercises_dataset:0002',canonical:'bench_press'}]);
});

test('maps safe muscle aliases and sorts secondary muscles', () => {
  const aliasConfig={...config,muscle_map:{...config.muscle_map,abdominals:'ABS','lower back':'BACK',brachialis:'BICEPS'}};
  const source=[{id:'0098',name:'Alias move',body_part:'waist',equipment:'body weight',target:'abdominals',secondary_muscles:['lower back','brachialis','lower back'],instructions:{en:'Move.',es:'Muévete.'}}];
  const result=buildCatalog(source,aliasConfig);
  assert.equal(result.records[0].primary_muscle,'ABS');
  assert.deepEqual(result.records[0].secondary_muscles,['BACK','BICEPS']);
  assert.deepEqual(result.report.unknown_muscle,[]);
});

test('reports every concrete discard reason independently', () => {
  const source=[{id:'bad',name:'',body_part:'waist',equipment:'body weight',target:'abs',instructions:{en:'',es:''}}];
  const result=buildCatalog(source,config);
  assert.deepEqual(result.report.discarded[0].reasons,['INVALID_ID','MISSING_NAME','MISSING_INSTRUCTIONS']);
});

test('classifies internal exact duplicates separately from short-signature candidates', () => {
  const first={id:'0100',name:'Shared move',body_part:'back',equipment:'dumbbell',target:'lats',secondary_muscles:['biceps'],instructions:{en:'Same instructions.',es:'Mismas instrucciones.'}};
  const exact={...first,id:'0101'};
  const candidate={...first,id:'0102',instructions:{en:'Different instructions.',es:'Instrucciones diferentes.'}};
  const result=buildCatalog([candidate,exact,first],config);
  assert.deepEqual(result.report.internal_duplicates,[
    {external_id:'exercises_dataset:0101',reference_external_id:'exercises_dataset:0100',classification:'EXACT'},
    {external_id:'exercises_dataset:0102',reference_external_id:'exercises_dataset:0100',classification:'CANDIDATE'}
  ]);
  assert.deepEqual(result.report.review.map((item)=>item.reasons),[
    ['INTERNAL_EXACT_DUPLICATE:exercises_dataset:0100'],
    ['INTERNAL_DUPLICATE_CANDIDATE:exercises_dataset:0100']
  ]);
  assert.equal(result.records.filter((record)=>record.import_status==='REVIEW').length,2);
});

test('applies only explicit per-record resolutions and keeps protected records inactive', () => {
  const resolutionConfig={...config,manual_resolutions:{
    '0101':{status:'MERGED',catalog_id:'exercises_dataset:0100',reason:'MANUALLY_CONFIRMED_DUPLICATE'},
    '0103':{status:'ACTIVE',ignore_secondary_muscles:['feet'],reason:'FEET_IS_NOT_A_MUSCLE_FILTER'},
    '0697':{status:'REVIEW',reason:'NAME_EQUIPMENT_INSTRUCTIONS_MISMATCH'},
    '0716':{status:'REVIEW',reason:'UNRESOLVED_STERNOCLEIDOMASTOID'},
    '1403':{status:'REVIEW',reason:'UNRESOLVED_STERNOCLEIDOMASTOID'},
    '1496':{status:'REVIEW',reason:'HAMMER_REQUIREMENT_UNRESOLVED'},
    '1766':{status:'REVIEW',reason:'NAME_EQUIPMENT_INSTRUCTIONS_MISMATCH'}
  }};
  const base={name:'Shared move',body_part:'back',equipment:'dumbbell',target:'lats',secondary_muscles:[],instructions:{en:'Move.',es:''}};
  const source=[
    {...base,id:'0100'},
    {...base,id:'0101'},
    {...base,id:'0102',name:'Almost shared move'},
    {...base,id:'0103',name:'Calf move',target:'calves',body_part:'lower legs',secondary_muscles:['feet']},
    ...['0697','0716','1403','1496','1766'].map((id)=>({...base,id,name:`Held ${id}`}))
  ];
  const mappedConfig={...resolutionConfig,muscle_map:{...resolutionConfig.muscle_map,calves:'CALVES'},body_region_map:{...resolutionConfig.body_region_map,'lower legs':'LOWER_LEGS'}};
  const {records,report}=buildCatalog(source,mappedConfig);
  assert.equal(records.find((r)=>r.external_id.endsWith('0101')).import_status,'MERGED');
  assert.equal(records.find((r)=>r.external_id.endsWith('0101')).proposed_canonical_reference,'exercises_dataset:0100');
  assert.equal(records.find((r)=>r.external_id.endsWith('0102')).import_status,'ACTIVE');
  assert.deepEqual(records.find((r)=>r.external_id.endsWith('0103')).secondary_muscles,[]);
  for(const id of ['0697','0716','1403','1496','1766']) {
    const held=records.find((r)=>r.external_id.endsWith(id));
    assert.equal(held.import_status,'REVIEW');
    assert.equal(held.proposed_canonical_reference,null);
  }
  assert.equal(report.counts.merged,1);
  assert.equal(report.counts.review,5);
});

test('production normalization manifest resolves the audited records deterministically', async () => {
  const configText=await readFile(new URL('./config.json',import.meta.url),'utf8');
  const resolutionText=await readFile(new URL('./manual-resolutions.json',import.meta.url),'utf8').catch(()=>null);
  assert.ok(resolutionText,'manual-resolutions.json must exist');
  const productionConfig=JSON.parse(configText);
  const resolutions=JSON.parse(resolutionText);
  assert.equal(productionConfig.normalization_rules_version,'fitplan-exercise-import-v2');
  for(const equipment of ['elliptical machine','skierg machine','sled machine','stationary bike','stepmill machine','tire','trap bar','upper body ergometer']) {
    assert.ok(productionConfig.equipment_map[equipment],`missing equipment mapping: ${equipment}`);
  }
  assert.deepEqual(productionConfig.equipment_map['body weight'].requirements,[]);
  for(const id of ['0697','0716','1403','1496','1766']) {
    assert.deepEqual(resolutions[id].status,'REVIEW');
  }
  for(const id of ['0577','0656','1371','1394','1628','1680','1731']) {
    assert.equal(resolutions[id].status,'MERGED');
    assert.match(resolutions[id].catalog_id,/^(?:exercises_dataset:\d{4}|[a-z_]+)$/);
  }
  assert.equal(Object.keys(resolutions).length,22);
});

test('attaches the audited resolution manifest to the production configuration', () => {
  assert.equal(typeof exerciseImporter.withManualResolutions,'function');
  const merged=exerciseImporter.withManualResolutions({marker:'config'},{'0697':{status:'REVIEW'}});
  assert.equal(merged.marker,'config');
  assert.deepEqual(merged.manual_resolutions,{'0697':{status:'REVIEW'}});
});

test('keeps Spanish aliases in a separate deterministic manifest', async () => {
  const aliases = JSON.parse(await readFile(new URL('./spanish-aliases.json', import.meta.url), 'utf8'));
  assert.deepEqual(Object.keys(aliases), [
    'abdominales', 'curl de bíceps', 'dominadas', 'elevaciones laterales', 'flexiones',
    'lagartijas', 'peso muerto', 'plancha', 'press de banca', 'remo', 'sentadilla', 'zancadas'
  ]);
  assert.deepEqual(aliases, {
    abdominales: 'crunch',
    'curl de bíceps': 'biceps_curl',
    dominadas: 'pull_up',
    'elevaciones laterales': 'lateral_raise',
    flexiones: 'push_up',
    lagartijas: 'push_up',
    'peso muerto': 'romanian_deadlift',
    plancha: 'plank',
    'press de banca': 'bench_press',
    remo: 'barbell_row',
    sentadilla: 'squat',
    zancadas: 'lunges'
  });
});
