import SwiftUI
import WidgetKit

struct MacroBarEntry: TimelineEntry {
    let date: Date
    let message: String
    let appGroupAvailable: Bool
}

struct MacroBarProvider: TimelineProvider {
    func placeholder(in context: Context) -> MacroBarEntry {
        MacroBarEntry(
            date: .now,
            message: "Hola desde MacroBar",
            appGroupAvailable: false
        )
    }

    func getSnapshot(in context: Context, completion: @escaping (MacroBarEntry) -> Void) {
        completion(makeEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<MacroBarEntry>) -> Void) {
        let entry = makeEntry()
        let nextRefresh = Date.now.addingTimeInterval(15 * 60)
        completion(Timeline(entries: [entry], policy: .after(nextRefresh)))
    }

    private func makeEntry() -> MacroBarEntry {
        let container = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: MacroBarShared.appGroupID
        )
        let available = container != nil
        let message = available
            ? (MacroBarShared.readMessage() ?? "Hola desde MacroBar")
            : "App Group no disponible"

        return MacroBarEntry(
            date: .now,
            message: message,
            appGroupAvailable: available
        )
    }
}

struct MacroBarWidgetEntryView: View {
    let entry: MacroBarEntry

    private let background = Color(red: 17.0 / 255.0, green: 24.0 / 255.0, blue: 32.0 / 255.0)
    private let green = Color(red: 126.0 / 255.0, green: 230.0 / 255.0, blue: 176.0 / 255.0)
    private let muted = Color(red: 143.0 / 255.0, green: 160.0 / 255.0, blue: 179.0 / 255.0)

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("MACROBAR · FASE 1")
                .font(.caption2.weight(.bold))
                .tracking(0.7)
                .foregroundStyle(green)

            Text(entry.message)
                .font(.headline)
                .foregroundStyle(.white)
                .lineLimit(3)
                .minimumScaleFactor(0.8)

            Spacer(minLength: 2)

            Label(
                entry.appGroupAvailable ? "App Group disponible" : "Pendiente de validar App Group",
                systemImage: entry.appGroupAvailable ? "checkmark.circle.fill" : "exclamationmark.circle"
            )
            .font(.caption2)
            .foregroundStyle(entry.appGroupAvailable ? green : muted)
            .lineLimit(2)
        }
        .padding()
        .containerBackground(background, for: .widget)
    }
}

@main
struct MacroBarWidget: Widget {
    let kind = "MacroBarWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: MacroBarProvider()) { entry in
            MacroBarWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("MacroBar")
        .description("Prueba inicial de la app y del dato compartido.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}
