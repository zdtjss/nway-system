import { defineStore } from 'pinia';
import { ElLoading } from 'element-plus';
import { useCurrentPageStore } from '@/stores/menuStore'

export const useIframeAppStore = defineStore('iframeApp', {
  state: () => {
    return {
      iframeList: [],
      loadingInstance: null,
    }
  },
  actions: {
    open(menu, iframeName) {

      console.log('打开iframe', menu);

      // 添加参数检查，这是必要的修改
      if (!menu || !menu.url) {
        console.warn('Invalid menu provided to iframeAppStore.open');
        return;
      }

      let iframe = this.iframeList.find(item => item.src === menu.host)
      if (!iframe) {

        this.loadingInstance = ElLoading.service({
          lock: true,
          text: '加载中...',
        });

        iframe = document.createElement('iframe')
        iframe.src = menu.host
        // 添加默认值，防止name为undefined
        iframe.name = menu.name || 'iframe_' + Date.now()
        iframe.id = menu.name || 'iframe_' + Date.now()
        iframe.style.cssText = 'border: none;display: none;'
        // 提取具名函数，确保添加和移除使用同一引用
        const onLoad = () => this.adjustIframe(iframe.id)
        iframe.addEventListener('load', onLoad)
        iframe._loadHandler = onLoad
        // 保存引用以便后续移除
        iframe._resizeHandler = () => this.adjustIframe(iframe.id)
        window.addEventListener('resize', iframe._resizeHandler);

        this.iframeList.push(iframe)
      } else {
        console.log('本页面已打开', iframe);
      }

      this.iframeList.forEach(f => {
        if (f === iframe) {
          const container = document.getElementById(iframeName || "currentMainIframe");
          if (container && !container.contains(iframe)) {
            console.log('页面未包含所需iframe', iframe);
            container.appendChild(iframe);
          }
          if (this.loadingInstance && this.loadingInstance.close) {
            this.loadingInstance.close();
            this.loadingInstance = null;
          }
        }
        f.style.display = f === iframe ? 'block' : 'none';
      });
    },
    adjustIframe(iframeId) {
      
      const { showIndex } = useCurrentPageStore()
      var ifm = document.getElementById(iframeId)
      if (!ifm) { return }
      ifm.height = document.documentElement.clientHeight - (showIndex ? 43 : 5)
      ifm.width = document.documentElement.clientWidth - 1
      ifm.style.cssText = 'border: none;display: block;'

      if (this.loadingInstance && this.loadingInstance.close) {
        this.loadingInstance.close()
        this.loadingInstance = null
      }
    },
    destroyIframe(url) {

      // url 为 buildApp 处理后的 host（与 open() 时 iframe.src 一致，已去除 query）
      // iframe src 可能带 query，故按前缀匹配
      const index = this.iframeList.findIndex(item => item.src.startsWith(url));
      if (index === -1) {
        console.warn('找不到 iframe:', url);
        return
      };

      let iframe = this.iframeList.splice(index, 1)[0];
      if (iframe._loadHandler) {
        iframe.removeEventListener('load', iframe._loadHandler);
      }
      if (iframe._resizeHandler) {
        window.removeEventListener('resize', iframe._resizeHandler);
      }

      try {
        if (iframe.src && !iframe.contentWindow.closed) {
          iframe.contentWindow.close();
        }
      } catch (e) {
        console.warn('无法访问 iframe 内容:', e);
      }

      iframe.src = '';
      if (iframe.parentNode) {
        iframe.parentNode.removeChild(iframe);
      }
    }
  }
})