import { expect, test } from '@playwright/test'
import { figmaState, SCREENS } from './figmaFixture'

for (const screen of SCREENS) {
  test(`402×886 ${screen.name}`, async ({ page }) => {
    await page.addInitScript((state) => {
      localStorage.setItem('peta.app.v2', JSON.stringify(state))
    }, screen.state ?? figmaState)
    await page.goto(`${screen.path}${screen.path.includes('?') ? '&' : '?'}shell=1`)
    await page.waitForLoadState('networkidle')
    await page.evaluate(() => document.fonts.ready)
    await page.locator('[role="status"]').waitFor({ state: 'detached' })
    await expect(page).toHaveScreenshot(`${screen.name}.png`, { maxDiffPixelRatio: 0.01 })
  })
}
