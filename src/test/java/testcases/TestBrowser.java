package testcases;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

public class TestBrowser {
    public static void main(String[] args) {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium()
                .launch(new BrowserType.LaunchOptions().setHeadless(false).setSlowMo(1000).setChannel("chrome"));
            Page page = browser.newPage();
            page.navigate("https://www.google.com");

            // Try common consent buttons (several options to cover locales)
            String[] consentSelectors = new String[] {
                "text=I agree",
                "text=Accept all",
                "text=Accept",
                "button:has-text(\"I agree\")",
                "button:has-text(\"Accept all\")",
                "button:has-text(\"Accept\")"
            };
            for (String sel : consentSelectors) {
                try {
                    Locator consent = page.locator(sel);
                    if (consent.count() > 0) {
                        consent.first().click();
                        break;
                    }
                } catch (PlaywrightException ignore) {
                }
            }

            // Robust search box locator: primary + fallbacks
            Locator searchBox = page.locator("input[name='q'], input[aria-label='Search'], input[title='Search'], textarea[name='q']");

            // Wait for the search box to be visible and enabled
            searchBox.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(5000));

            // Fill and submit
            searchBox.first().fill("hello automation");
            searchBox.first().press("Enter");

            // Wait for network idle or DOM load (some Google variations do client-side navigation)
            try {
                page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(15000));
            } catch (PlaywrightException ignore) {
                // load state may not reach NETWORKIDLE quickly; continue to wait for results below
            }

            // Wait for results: try #search first, then fall back to role=main or result headings (h3)
            boolean resultsFound = false;
            try {
                page.waitForSelector("#search", new Page.WaitForSelectorOptions().setTimeout(15000).setState(WaitForSelectorState.VISIBLE));
                resultsFound = true;
            } catch (PlaywrightException e) {
                // try fallback selectors
            }

            if (!resultsFound) {
                try {
                    page.waitForSelector("div[role='main']", new Page.WaitForSelectorOptions().setTimeout(15000).setState(WaitForSelectorState.VISIBLE));
                    resultsFound = true;
                } catch (PlaywrightException e) {
                    // try another fallback
                }
            }

            if (!resultsFound) {
                try {
                    page.waitForSelector("h3", new Page.WaitForSelectorOptions().setTimeout(15000).setState(WaitForSelectorState.VISIBLE));
                    resultsFound = true;
                } catch (PlaywrightException e) {
                    // final fallback failed
                }
            }

            if (!resultsFound) {
                System.out.println("Search results did not appear within timeout. You may need to increase timeouts or check for consent overlays.");
            } else {
                // Ensure network activity has settled before reading title
                try {
                    page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
                } catch (PlaywrightException ignored) {
                }
                System.out.println("Page title: " + page.title());
            }

            page.close();
            browser.close();
        }
    }
}
