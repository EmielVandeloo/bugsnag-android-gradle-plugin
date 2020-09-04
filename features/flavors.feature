Feature: Plugin integrated in project with productFlavors

Scenario: Flavors automatic upload on build
    When I build "flavors" using the "standard" bugsnag config
    And I wait to receive 4 requests

    Then the request is valid for the Build API
    And the payload field "appVersion" equals "1.0"
    And the payload field "apiKey" equals "TEST_API_KEY"
    And the payload field "appVersionCode" equals "1"
    And I discard the oldest request

    And the request is valid for the Android Mapping API
    And the field "apiKey" for multipart request equals "TEST_API_KEY"
    And the field "versionCode" for multipart request equals "1"
    And the field "versionName" for multipart request equals "1.0"
    And the field "appId" for multipart request equals "com.bugsnag.android.example.bar"
    And I discard the oldest request

    And the request is valid for the Build API
    And the payload field "appVersion" equals "1.0"
    And the payload field "apiKey" equals "TEST_API_KEY"
    And the payload field "appVersionCode" equals "1"
    And I discard the oldest request

    And the request is valid for the Android Mapping API
    And the field "apiKey" for multipart request equals "TEST_API_KEY"
    And the field "versionCode" for multipart request equals "1"
    And the field "versionName" for multipart request equals "1.0"
    And the field "appId" for multipart request equals "com.bugsnag.android.example.foo"

Scenario: Flavors automatic upload disabled
    When I build "flavors" using the "all_disabled" bugsnag config
    And I wait for 5 seconds
    Then I should receive no requests

@skip_agp3_5
Scenario: Flavors manual upload of build API
    When I build the "Foo-release" variantOutput for "flavors" using the "all_disabled" bugsnag config
    And I wait to receive 1 requests
    Then the request is valid for the Android Mapping API
    And the field "apiKey" for multipart request equals "TEST_API_KEY"
    And the field "versionCode" for multipart request equals "1"
    And the field "versionName" for multipart request equals "1.0"
    And the field "appId" for multipart request equals "com.bugsnag.android.example.foo"
