// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "RovexAPKGSmoke",
    platforms: [.macOS(.v13)],
    dependencies: [
        .package(url: "https://github.com/weichsel/ZIPFoundation.git", from: "0.9.19")
    ],
    targets: [
        .executableTarget(
            name: "APKGImportSmoke",
            dependencies: [.product(name: "ZIPFoundation", package: "ZIPFoundation")],
            path: ".",
            exclude: [
                "Rovex.xcodeproj",
                "Rovex/Views",
                "Rovex/RovexApp.swift",
                "Rovex/Services/LocalStudyStore.swift",
                "Rovex/Services/SQLiteQBankImporter.swift",
                "tests/ImportCompatibilitySmoke.swift"
            ],
            sources: [
                "Rovex/Models/StudyModels.swift",
                "Rovex/Services/APKGImporter.swift",
                "tests/APKGImportSmoke.swift"
            ],
            linkerSettings: [.linkedLibrary("sqlite3")]
        )
    ]
)
