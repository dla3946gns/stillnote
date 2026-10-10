import SwiftUI
import UIKit

enum Theme {
    static let cream = color(0xF5EFE3, dark: 0x29221E)
    static let ivory = color(0xFFFCF6, dark: 0x221C18)
    static let cocoa = color(0x7A5746, dark: 0xD4AD94)
    static let apricot = color(0xEBD8C5, dark: 0x4B382C)
    static let ink = color(0x352B25, dark: 0xF7EBDD)
    static let muted = color(0x756557, dark: 0xBCA996)
    static let line = color(0xE6DCCF, dark: 0x4C4037)

    private static func color(_ light: UInt, dark: UInt) -> Color {
        Color(uiColor: UIColor { traits in
            let hex = traits.userInterfaceStyle == .dark ? dark : light
            return UIColor(
                red: CGFloat((hex >> 16) & 255) / 255,
                green: CGFloat((hex >> 8) & 255) / 255,
                blue: CGFloat(hex & 255) / 255,
                alpha: 1
            )
        })
    }
}
