// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "GuHealthCore",
    platforms: [.macOS(.v13), .iOS(.v17)],
    products: [.library(name: "GuHealthCore", targets: ["GuHealthCore"])],
    targets: [
        .target(name: "GuHealthCore", path: "GuHealth/Core"),
        .testTarget(name: "GuHealthCoreTests", dependencies: ["GuHealthCore"], path: "GuHealthCoreTests")
    ]
)
