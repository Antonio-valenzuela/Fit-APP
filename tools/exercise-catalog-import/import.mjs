import { createHash } from 'node:crypto';
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { gzipSync } from 'node:zlib';

export function normalizeMatchText(value) {
  return String(value ?? '').normalize('NFD').replace(/\p{M}+/gu, '').toLowerCase()
    .replace(/[^a-z0-9]+/g, ' ').trim().replace(/\s+/g, ' ');
}
const sha256 = (bytes) => createHash('sha256').update(bytes).digest('hex');
const sorted = (values) => [...new Set(values)].sort();

export function serializeCanonical(value) { return Buffer.from(JSON.stringify(value)); }
export function gzipCanonical(bytes) { return gzipSync(bytes,{mtime:0}); }

export function withManualResolutions(config, manualResolutions) {
  return {...config,manual_resolutions:manualResolutions};
}

export function buildCatalog(source, config) {
  if (!Array.isArray(source)) throw new Error('Source must be an array');
  const builtins = (config.builtins ?? []).map((b) => Array.isArray(b)
    ? { id:b[0], name:b[1], primary_muscle:b[2], equipment:b[3] } : b);
  const builtinBySignature = new Map(builtins.map((b) => [`${normalizeMatchText(b.name)}|${b.primary_muscle}|${b.equipment}`, b.id]));
  const seenIds = new Set(), firstByCandidateSignature = new Map(), records = [], review = [], discarded = [];
  const unknown = { equipment:new Set(), muscle:new Set(), category:new Set() };
  const missing = { spanish_name:[], english_name:[], spanish_instructions:[], english_instructions:[] };
  const duplicate_ids=[] , invalid_ids=[], internal_duplicates=[], exact_builtin_matches=[];
  for (const raw of [...source].sort((a,b) => String(a.id).localeCompare(String(b.id)))) {
    const id=String(raw.id ?? '').trim();
    const nameEs=String(raw.name_es ?? '').trim() || null;
    const nameEn=String(raw.name_en ?? raw.name ?? '').trim() || null;
    const instructionsEs=String(raw.instructions?.es ?? raw.instructions_es ?? '').trim() || null;
    const instructionsEn=String(raw.instructions?.en ?? raw.instructions_en ?? '').trim() || null;
    const reasons=[];
    if (!/^\d{4}$/.test(id) || (!nameEs && !nameEn) || (!instructionsEs && !instructionsEn)) {
      const invalidReasons=[];
      if(!/^\d{4}$/.test(id)){invalid_ids.push(id||null);invalidReasons.push('INVALID_ID');}
      if(!nameEs && !nameEn) invalidReasons.push('MISSING_NAME');
      if(!instructionsEs && !instructionsEn) invalidReasons.push('MISSING_INSTRUCTIONS');
      discarded.push({source_id:id||null,reasons:invalidReasons});
      continue;
    }
    if (seenIds.has(id)) { duplicate_ids.push(id); discarded.push({source_id:id,reasons:['DUPLICATE_SOURCE_ID']}); continue; }
    seenIds.add(id);
    const resolution=config.manual_resolutions?.[id] ?? null;
    const target=normalizeMatchText(raw.target), category=normalizeMatchText(raw.body_part ?? raw.category), equipmentKey=normalizeMatchText(raw.equipment);
    const primary=config.muscle_map[target]; const region=config.body_region_map[category]; let equip=config.equipment_map[equipmentKey];
    if (!primary) { reasons.push(`UNKNOWN_MUSCLE:${target}`); unknown.muscle.add(target); }
    if (!region) { reasons.push(`UNKNOWN_CATEGORY:${category}`); unknown.category.add(category); }
    if (!equip) { reasons.push(`UNKNOWN_EQUIPMENT:${equipmentKey}`); unknown.equipment.add(equipmentKey); equip={enum:'OTHER',requirements:[String(raw.equipment??'unknown')],location:'GYM_COMPATIBLE'}; }
    const ignoredSecondary=new Set((resolution?.ignore_secondary_muscles ?? []).map(normalizeMatchText));
    const secondaryValues=[];
    for(const value of (Array.isArray(raw.secondary_muscles)?raw.secondary_muscles:[])){
      const key=normalizeMatchText(value);
      if(ignoredSecondary.has(key)) continue;
      const mapped=config.muscle_map[key];
      if(mapped) secondaryValues.push(mapped); else { reasons.push(`UNKNOWN_SECONDARY_MUSCLE:${key}`); unknown.muscle.add(key); }
    }
    const secondary=sorted(secondaryValues);
    if (!nameEs) missing.spanish_name.push(id);
    if (!nameEn) missing.english_name.push(id);
    if (!instructionsEs) missing.spanish_instructions.push(id);
    if (!instructionsEn) missing.english_instructions.push(id);
    const matchName=nameEs ?? nameEn;
    const requirements=equip.enum==='BODY_WEIGHT' ? [] : sorted(equip.requirements ?? []);
    const signature=`${normalizeMatchText(matchName)}|${primary??'UNKNOWN'}|${equip.enum}`;
    const externalId=`exercises_dataset:${id}`;
    const canonical=builtinBySignature.get(signature) ?? null;
    if(canonical) exact_builtin_matches.push({external_id:externalId,canonical});
    let status=canonical?'MERGED':reasons.length?'REVIEW':'ACTIVE';
    let canonicalReference=canonical;
    const exactContent={name_es:nameEs,name_en:nameEn,instructions_es:instructionsEs,instructions_en:instructionsEn,primary_muscle:primary??'UNKNOWN',secondary_muscles:secondary,body_region:region??'UNKNOWN',equipment:equip.enum,equipment_requirements:requirements,location_compatibility:equip.location};
    const exactSignature=JSON.stringify(exactContent), first=firstByCandidateSignature.get(signature);
    if(first){ const classification=first.exactSignature===exactSignature?'EXACT':'CANDIDATE'; status='REVIEW'; reasons.push(`${classification==='EXACT'?'INTERNAL_EXACT_DUPLICATE':'INTERNAL_DUPLICATE_CANDIDATE'}:${first.externalId}`); internal_duplicates.push({external_id:externalId,reference_external_id:first.externalId,classification}); }
    else firstByCandidateSignature.set(signature,{externalId,exactSignature});
    if(resolution){
      status=resolution.status;
      canonicalReference=resolution.status==='MERGED' ? resolution.catalog_id : null;
      reasons.length=0;
      if(resolution.reason) reasons.push(resolution.reason);
    }
    const record={external_id:externalId,...exactContent,
      import_status:status,
      proposed_canonical_reference:canonicalReference};
    records.push(record); if (status==='REVIEW') review.push({external_id:record.external_id,reasons});
  }
  records.sort((a,b)=>a.external_id.localeCompare(b.external_id));
  const counts={read:source.length,valid:records.length,active:records.filter(r=>r.import_status==='ACTIVE').length,
    merged:records.filter(r=>r.import_status==='MERGED').length,review:records.filter(r=>r.import_status==='REVIEW').length,discarded:discarded.length};
  const missingTranslations={};
  for(const [key,ids] of Object.entries(missing)) missingTranslations[key]={count:ids.length,ids};
  const report={counts,exact_builtin_matches,review,discarded,internal_duplicates,unknown_equipment:sorted(unknown.equipment),unknown_muscle:sorted(unknown.muscle),unknown_category:sorted(unknown.category),missing_translations:missingTranslations,duplicate_ids,invalid_ids};
  return {records,report};
}

function markdown(report) {
  const c=report.counts;
  const review=report.review.map((item)=>`- ${item.external_id}: ${item.reasons.join('; ')}`).join('\n')||'- None';
  const discarded=report.discarded.map((item)=>`- ${item.source_id ?? '(missing id)'}: ${item.reasons.join('; ')}`).join('\n')||'- None';
  const exact=report.exact_builtin_matches.map((x)=>`- ${x.external_id}: ${x.canonical}`).join('\n')||'- None';
  const metrics=report.metrics??{};
  const internal=report.internal_duplicates.map((x)=>`- ${x.external_id} -> ${x.reference_external_id} (${x.classification})`).join('\n')||'- None';
  return `# Exercise catalog import report\n\n- Read: ${c.read}\n- Valid: ${c.valid}\n- Active: ${c.active}\n- Merged: ${c.merged}\n- Review: ${c.review}\n- Discarded: ${c.discarded}\n\n## Metrics\n\n- Source bytes: ${metrics.source_bytes??'N/A'}\n- Compact bytes: ${metrics.compact_bytes??'N/A'}\n- Gzip bytes: ${metrics.gzip_bytes??'N/A'}\n- Final records: ${metrics.final_records??'N/A'}\n\n## Exact built-in matches\n\n${exact}\n\n## Internal duplicates\n\n${internal}\n\n## Duplicate IDs\n\n${report.duplicate_ids.join(', ')||'None'}\n\n## Invalid IDs\n\n${report.invalid_ids.join(', ')||'None'}\n\n## Review reasons\n\n${review}\n\n## Discard reasons\n\n${discarded}\n\n## Missing translations\n\n- Spanish names: ${report.missing_translations.spanish_name.count} (${report.missing_translations.spanish_name.ids.join(', ')})\n- English names: ${report.missing_translations.english_name.count} (${report.missing_translations.english_name.ids.join(', ')})\n- Spanish instructions: ${report.missing_translations.spanish_instructions.count} (${report.missing_translations.spanish_instructions.ids.join(', ')})\n- English instructions: ${report.missing_translations.english_instructions.count} (${report.missing_translations.english_instructions.ids.join(', ')})\n\n## Unknown values\n\n- Equipment: ${report.unknown_equipment.join(', ')||'None'}\n- Muscles: ${report.unknown_muscle.join(', ')||'None'}\n- Categories: ${report.unknown_category.join(', ')||'None'}\n`;
}

async function main() {
  const started=process.hrtime.bigint(); const dir=new URL('./',import.meta.url);
  const baseConfig=JSON.parse(await readFile(new URL('config.json',dir),'utf8'));
  const resolutionBytes=await readFile(new URL('manual-resolutions.json',dir));
  const config=withManualResolutions(baseConfig,JSON.parse(resolutionBytes));
  const url=`${config.repository}/raw/${config.commit}/data/exercises.json`;
  const response=await fetch(url,{headers:{'User-Agent':'FitPlan-exercise-import'}}); if(!response.ok) throw new Error(`Fetch failed: ${response.status}`);
  const sourceBytes=Buffer.from(await response.arrayBuffer()), sourceSha=sha256(sourceBytes);
  if (process.argv.includes('--source-sha')) { console.log(sourceSha); return; }
  if (sourceSha!==config.expected_source_sha256) throw new Error(`Source SHA256 mismatch: expected ${config.expected_source_sha256}, received ${sourceSha}`);
  const source=JSON.parse(sourceBytes); if(source.length!==config.expected_records) throw new Error(`Expected ${config.expected_records}, received ${source.length}`);
  const {records,report}=buildCatalog(source,config); const compact=serializeCanonical(records), gz=gzipCanonical(compact);
  report.metrics={source_bytes:sourceBytes.length,compact_bytes:compact.length,gzip_bytes:gz.length,final_records:records.length};
  const forbidden=/\b(?:image|gif_url|media_id|created_at|attribution)\b|gymvisual|\.(?:gif|jpe?g|png)\b/i;
  if(forbidden.test(compact.toString('utf8'))) throw new Error('Forbidden multimedia data detected');
  const provenance={repository:config.repository,commit:config.commit,source_sha256:sourceSha,compact_sha256:sha256(compact),manual_resolutions_sha256:sha256(resolutionBytes),normalization_rules_version:config.normalization_rules_version,import_version:config.import_version,generation_date:config.generation_date,config:{expected_records:config.expected_records,languages:['es','en'],external_id_namespace:'exercises_dataset'}};
  const out=new URL('./out/',dir); await rm(out,{recursive:true,force:true}); await mkdir(out,{recursive:true});
  await Promise.all([writeFile(new URL('exercises.compact.json',out),compact),writeFile(new URL('exercises.compact.json.gz',out),gz),writeFile(new URL('report.json',out),serializeCanonical(report)),writeFile(new URL('report.md',out),markdown(report)),writeFile(new URL('provenance.json',out),serializeCanonical(provenance))]);
  const elapsed=Number(process.hrtime.bigint()-started)/1e6;
  console.log(JSON.stringify({commit:config.commit,source_sha256:sourceSha,compact_sha256:provenance.compact_sha256,source_bytes:sourceBytes.length,compact_bytes:compact.length,gzip_bytes:gz.length,records:records.length,counts:report.counts,...(process.argv.includes('--benchmark')?{benchmark_ms:Number(elapsed.toFixed(2))}:{})}));
}
if (process.argv[1] && import.meta.url===new URL(`file:///${process.argv[1].replace(/\\/g,'/')}`).href) main().catch((e)=>{console.error(e.message);process.exitCode=1;});
