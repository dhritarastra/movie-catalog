import { defineConfig, devices } from '@playwright/test';

// Runs against the Docker stack (docker compose up), through nginx.
export default defineConfig({
	testDir: 'e2e',
	timeout: 60_000,
	retries: process.env.CI ? 1 : 0,
	reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
	use: {
		baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3000',
		trace: 'retain-on-failure'
	},
	projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }]
});
