import Foundation

enum MacroBarShared {
    static let appGroupID = "group.com.antoniovalenzuela.macrobar"
    static let messageKey = "macrobar.phase1.message"

    static func readMessage() -> String? {
        UserDefaults(suiteName: appGroupID)?.string(forKey: messageKey)
    }
}
