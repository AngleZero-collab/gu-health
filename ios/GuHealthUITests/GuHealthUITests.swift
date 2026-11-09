import XCTest

final class GuHealthUITests: XCTestCase {
    @MainActor
    func testThreeTabsAndDemoProfilePersistAcrossLaunch() {
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing"]
        app.launch()
        let demo = app.buttons["先看看示範資料"]
        if demo.waitForExistence(timeout: 3) { demo.tap() }
        XCTAssertTrue(app.staticTexts["greeting"].waitForExistence(timeout: 5))
        app.buttons["tab-1"].tap()
        XCTAssertTrue(app.staticTexts["居家視覺觀察"].exists)
        app.buttons["tab-2"].tap()
        XCTAssertTrue(app.buttons["新增健康紀錄"].exists)
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "我的紀錄"; attachment.lifetime = .keepAlways; add(attachment)
        app.terminate(); app.launch()
        XCTAssertTrue(app.staticTexts["greeting"].waitForExistence(timeout: 5))
    }

    @MainActor
    func testObservationCanBeCancelledWithoutSaving() {
        let app = XCUIApplication(); app.launchArguments = ["-ui-testing"]; app.launch()
        let demo = app.buttons["先看看示範資料"]
        if demo.waitForExistence(timeout: 3) { demo.tap() }
        app.buttons["tab-1"].tap()
        app.buttons["開始雙眼方格觀察"].tap()
        app.buttons["我已了解，開始"].tap()
        XCTAssertTrue(app.staticTexts["請遮住右眼\n用左眼看中央黑點"].exists)
        app.buttons["沒有明顯變化"].tap()
        XCTAssertTrue(app.staticTexts["請遮住左眼\n用右眼看中央黑點"].exists)
        app.swipeUp()
        app.buttons["離開這次觀察"].tap()
        app.alerts.buttons["離開且不儲存"].tap()
        XCTAssertTrue(app.staticTexts["居家視覺觀察"].waitForExistence(timeout: 5))
    }
}
