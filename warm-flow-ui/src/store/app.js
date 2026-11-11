
const useAppStore = defineStore(
  'app',
  {
    state: () => ({
      appParams: null
    }),
    actions: {
      // 认证走同源 Cookie；设计器只解析 iframe URL 上的页面参数
      async fetchConfig() {
        const urlParams = new URLSearchParams(window.location.search);
        const params = {};
        this.appParams = null
        for (const [key, value] of urlParams.entries()) {
          if (value && 'undefined' !== value) {
            params[key] = value;
          }
        }
        this.appParams = params;
      }
    }
  })

export default useAppStore;
