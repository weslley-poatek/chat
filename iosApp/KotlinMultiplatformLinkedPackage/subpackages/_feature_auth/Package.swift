// swift-tools-version: 5.9
import PackageDescription
let package = Package(
  name: "_feature_auth",
  platforms: [
    .iOS("15.0")
  ],
  products: [
    .library(
      name: "_feature_auth",
      type: .none,
      targets: ["_feature_auth"]
    )
  ],
  dependencies: [
    .package(
      url: "https://github.com/google/GoogleSignIn-iOS.git",
      exact: "10.0.0"
    )
  ],
  targets: [
    .target(
      name: "_feature_auth",
      dependencies: [
        .product(
          name: "GoogleSignIn",
          package: "GoogleSignIn-iOS",
          condition: .when(platforms: [.iOS])
        )
      ]
    )
  ]
)
