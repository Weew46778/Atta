import {defineConfig} from '@playwright/test';
import chromium from '@sparticuz/chromium';
export default defineConfig({
  testDir:'./tests/v3',
  fullyParallel:false,
  timeout:240000,
  workers:1,
  reporter:[['list'],['json',{outputFile:'test-results/v3-results.json'}]],
  use:{
    baseURL:'http://localhost:5173',
    actionTimeout:15000,
    viewport:{width:430,height:900},
    reducedMotion:'reduce',
    launchOptions:{
      executablePath:await chromium.executablePath(),
      args:['--no-sandbox','--disable-dev-shm-usage'],
      env:{...process.env,LD_LIBRARY_PATH:process.cwd()+'/.tools/chromium-libs/lib'}
    },
    screenshot:'only-on-failure'
  },
  webServer:{command:'npm run dev -- --port 5173',url:'http://localhost:5173',reuseExistingServer:true,timeout:120000}
});
