import SwiftUI
import WidgetKit

struct ContentView: View {
    @State private var sharedValue = MacroBarShared.readMessage() ?? "Hola desde MacroBar"
    @State private var shareStatus = "Pulsa el botón para probar la comunicación con el widget."

    private let background = Color(red: 17.0 / 255.0, green: 24.0 / 255.0, blue: 32.0 / 255.0)
    private let green = Color(red: 126.0 / 255.0, green: 230.0 / 255.0, blue: 176.0 / 255.0)
    private let muted = Color(red: 143.0 / 255.0, green: 160.0 / 255.0, blue: 179.0 / 255.0)

    var body: some View {
        ZStack {
            background.ignoresSafeArea()

            VStack(alignment: .leading, spacing: 20) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("MACROBAR · FASE 1")
                        .font(.caption.weight(.bold))
                        .tracking(1.3)
                        .foregroundStyle(green)

                    Text("Hola, iPhone.")
                        .font(.largeTitle.bold())
                        .foregroundStyle(.white)

                    Text("Primero validamos la app, el widget y el dato compartido. Después vienen los macros.")
                        .font(.subheadline)
                        .foregroundStyle(muted)
                }

                VStack(alignment: .leading, spacing: 10) {
                    Text("Dato actual")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(muted)

                    Text(sharedValue)
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(.white)
                        .lineLimit(3)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16)
                        .background(Color(red: 35.0 / 255.0, green: 44.0 / 255.0, blue: 56.0 / 255.0))
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                }

                Button(action: writeSharedValue) {
                    Label("Probar App Group", systemImage: "arrow.triangle.2.circlepath")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                }
                .buttonStyle(.borderedProminent)
                .tint(green)
                .foregroundStyle(background)

                Text(shareStatus)
                    .font(.footnote)
                    .foregroundStyle(muted)
                    .frame(maxWidth: .infinity, alignment: .leading)

                Spacer(minLength: 0)
            }
            .padding(24)
        }
        .preferredColorScheme(.dark)
        .onAppear(perform: inspectSharedContainer)
    }

    private func inspectSharedContainer() {
        let container = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: MacroBarShared.appGroupID
        )

        if container == nil {
            shareStatus = "App Group no disponible con la firma actual. Lo estamos validando en esta fase."
        } else {
            shareStatus = "Contenedor App Group disponible. Pulsa el botón para probar la escritura."
        }
    }

    private func writeSharedValue() {
        guard FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: MacroBarShared.appGroupID
        ) != nil,
        let defaults = UserDefaults(suiteName: MacroBarShared.appGroupID) else {
            shareStatus = "No se pudo abrir App Group. Puede ser una limitación de la firma gratuita."
            return
        }

        let value = "Escrito desde la app · " + Date.now.formatted(date: .omitted, time: .shortened)
        defaults.set(value, forKey: MacroBarShared.messageKey)
        defaults.synchronize()

        sharedValue = defaults.string(forKey: MacroBarShared.messageKey) ?? value
        shareStatus = "Dato guardado. Añade MacroBar a la pantalla de inicio; iOS puede tardar un poco en refrescarlo."
        WidgetCenter.shared.reloadAllTimelines()
    }
}
