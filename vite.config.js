import {defineConfig} from 'vite';
export default defineConfig({base:'./',server:{host:'0.0.0.0',allowedHosts:true,proxy:{'/api':{target:'http://127.0.0.1:8787'}}},build:{rollupOptions:{input:{main:'index.html',legacy:'legacy.html'}}}});
