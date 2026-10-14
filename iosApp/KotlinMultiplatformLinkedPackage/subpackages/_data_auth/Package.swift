// swift-tools-version: 5.9
import PackageDescription
let package = Package(
  name: "_data_auth",
  platforms: [
    .iOS("15.0")
  ],
  products: [
    .library(
      name: "_data_auth",
      type: .none,
      targets: ["_data_auth"]
    )
  ],
  dependencies: [
    .package(
      url: "https://github.com/firebase/firebase-ios-sdk.git",
      exact: "12.19.2"
    )
  ],
  targets: [
    .target(
      name: "_data_auth",
      dependencies: [
        .product(
          name: "FirebaseAuth",
          package: "firebase-ios-sdk",
          condition: .when(platforms: [.iOS])
        )
      ]
    )
  ]
)
