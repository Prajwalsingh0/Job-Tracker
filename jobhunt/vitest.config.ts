import { defineConfig, mergeConfig } from 'vitest/config';
import viteConfig from './vite.config';

/**
 * Test-only configuration.
 *
 * It lives in its own file (rather than a `test` block inside vite.config.ts) because
 * Vitest 2 is typed against Vite 5 while this project runs Vite 6, so mixing the two
 * `defineConfig` signatures breaks `tsc -b`. tsconfig.node.json only includes
 * vite.config.ts, so this file is used by Vitest but never type-checked by the build.
 *
 * The Vite config is merged in so the `@/` alias and the React plugin apply to tests.
 */
export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      include: ['src/**/*.{test,spec}.{ts,tsx}'],
      restoreMocks: true,
    },
  }),
);
