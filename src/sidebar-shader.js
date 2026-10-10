import { createShader } from 'shaders/js';

export async function startSidebarShader() {
  const canvas = document.getElementById('sidebarShader');
  const host = canvas.parentElement;
  const motion = matchMedia('(prefers-reduced-motion: reduce)');
  if (motion.matches || navigator.connection?.saveData) return;
  let inView = true;
  let shader;
  const sync = () => {
    if (!shader) return;
    const { width, height } = host.getBoundingClientRect();
    if (document.hidden || motion.matches || !inView || !width || !height) {
      shader.pause();
    } else {
      shader.resize(width, height);
      shader.resume();
    }
  };
  shader = await createShader(canvas, {
    components: [{
      type: 'MeshGradient',
      props: { stops: null, colorA: '#d9e5d6', colorB: '#f4efdf', speed: .1, colorSpace: 'oklab', count: 3, variation: 0, drift: .15, swirl: .08 }
    }]
  }, {
    disableTelemetry: true,
    observeElement: false,
    onReady: sync,
    onError: () => {
      if (shader?.getFailureReason()) canvas.classList.remove('is-ready');
    }
  });

  if (shader.getFailureReason()) return;
  canvas.classList.add('is-ready');
  const sizeObserver = new ResizeObserver(sync);
  const visibilityObserver = new IntersectionObserver(([entry]) => {
    inView = entry.isIntersecting;
    sync();
  });
  sizeObserver.observe(host);
  visibilityObserver.observe(host);
  motion.addEventListener('change', sync);
  document.addEventListener('visibilitychange', sync);
  const cleanup = () => {
    sizeObserver.disconnect();
    visibilityObserver.disconnect();
    motion.removeEventListener('change', sync);
    document.removeEventListener('visibilitychange', sync);
    window.removeEventListener('pageshow', onPageShow);
    window.removeEventListener('pagehide', onPageHide);
    shader.destroy();
    canvas.classList.remove('is-ready');
  };
  const onPageShow = event => {
    if (event.persisted) {
      cleanup();
      startSidebarShader().catch(() => {});
    } else sync();
  };
  const onPageHide = event => {
    shader.pause();
    if (!event.persisted) cleanup();
  };
  window.addEventListener('pageshow', onPageShow);
  window.addEventListener('pagehide', onPageHide);
  sync();
}
