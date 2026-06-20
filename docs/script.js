// ============================================
// JP Media Viewer - Interactive Effects
// Inspired by vulnclaw.com effects
// Catppuccin Mocha Color Scheme
// ============================================

(function() {
  'use strict';

  // ---- WebGL Particle Tunnel Background ----
  function initWebGL() {
    const canvas = document.getElementById('webgl-bg');
    if (!canvas) return;
    
    if (window.innerWidth < 768) {
      canvas.style.display = 'none';
      return;
    }

    const cleanupFunctions = [];

    const gl = canvas.getContext('webgl') || canvas.getContext('experimental-webgl');
    if (!gl) return;

    const resizeHandler = () => {
      canvas.width = window.innerWidth;
      canvas.height = window.innerHeight;
      gl.viewport(0, 0, canvas.width, canvas.height);
    };
    resizeHandler();
    window.addEventListener('resize', resizeHandler);
    cleanupFunctions.push(() => window.removeEventListener('resize', resizeHandler));

    // Vertex shader
    const vsSource = `
      attribute vec2 aPosition;
      varying vec2 vUV;
      void main() {
        vUV = aPosition * 0.5 + 0.5;
        gl_Position = vec4(aPosition, 0.0, 1.0);
      }
    `;

    // Fragment shader - particle tunnel effect
    const fsSource = `
      precision mediump float;
      varying vec2 vUV;
      uniform float uTime;
      uniform vec2 uResolution;
      uniform vec2 uMouse;

      void main() {
        vec2 uv = vUV;
        vec2 mouse = uMouse / uResolution;
        
        // Create tunnel effect
        vec2 center = vec2(0.5);
        vec2 delta = uv - center;
        float dist = length(delta);
        float angle = atan(delta.y, delta.x);
        
        // Particles
        float t = uTime * 0.3;
        vec3 color = vec3(0.0);
        
        for (float i = 0.0; i < 8.0; i++) {
          float layer = mod(dist * 8.0 + t + i * 0.5, 1.0);
          float alpha = smoothstep(0.05, 0.0, abs(layer - 0.5)) * 0.3;
          
          float particleAngle = angle * (3.0 + i) + t * (1.0 + i * 0.2);
          float px = cos(particleAngle) * 0.5 + 0.5;
          float py = sin(particleAngle) * 0.5 + 0.5;
          float pd = length(uv - vec2(px, py));
          float particle = smoothstep(0.02, 0.0, pd) * alpha;
          
          // Catppuccin Mocha colors
          vec3 c1 = vec3(0.8, 0.65, 0.97); // mauve
          vec3 c2 = vec3(0.54, 0.71, 0.98); // blue
          vec3 c3 = vec3(0.58, 0.89, 0.84); // teal
          color += mix(c1, c2, sin(i + t) * 0.5 + 0.5) * particle;
          color += mix(c2, c3, cos(i * 0.7 + t) * 0.5 + 0.5) * particle * 0.5;
        }
        
        // Ambient glow
        float glow = exp(-dist * 3.0) * 0.15;
        color += vec3(0.8, 0.65, 0.97) * glow;
        
        // Mouse influence
        float mouseDist = length(uv - mouse);
        color += vec3(0.54, 0.71, 0.98) * smoothstep(0.3, 0.0, mouseDist) * 0.1;
        
        gl_FragColor = vec4(color, 1.0);
      }
    `;

    function compileShader(source, type) {
      const shader = gl.createShader(type);
      gl.shaderSource(shader, source);
      gl.compileShader(shader);
      return shader;
    }

    const vs = compileShader(vsSource, gl.VERTEX_SHADER);
    const fs = compileShader(fsSource, gl.FRAGMENT_SHADER);

    const program = gl.createProgram();
    gl.attachShader(program, vs);
    gl.attachShader(program, fs);
    gl.linkProgram(program);
    gl.useProgram(program);

    // Full-screen quad
    const vertices = new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]);
    const buffer = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
    gl.bufferData(gl.ARRAY_BUFFER, vertices, gl.STATIC_DRAW);

    const aPos = gl.getAttribLocation(program, 'aPosition');
    gl.enableVertexAttribArray(aPos);
    gl.vertexAttribPointer(aPos, 2, gl.FLOAT, false, 0, 0);

    const uTime = gl.getUniformLocation(program, 'uTime');
    const uRes = gl.getUniformLocation(program, 'uResolution');
    const uMouse = gl.getUniformLocation(program, 'uMouse');

    let mouseX = canvas.width / 2;
    let mouseY = canvas.height / 2;
    let animFrameId;

    const mouseHandler = (e) => {
      mouseX = e.clientX;
      mouseY = canvas.height - e.clientY;
    };
    document.addEventListener('mousemove', mouseHandler);
    cleanupFunctions.push(() => document.removeEventListener('mousemove', mouseHandler));

    let startTime = Date.now();

    function render() {
      const time = (Date.now() - startTime) / 1000;
      gl.uniform1f(uTime, time);
      gl.uniform2f(uRes, canvas.width, canvas.height);
      gl.uniform2f(uMouse, mouseX, mouseY);
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
      animFrameId = requestAnimationFrame(render);
    }
    render();

    return () => {
      cleanupFunctions.forEach(fn => fn());
      cancelAnimationFrame(animFrameId);
    };
  }

  // ---- Custom Cursor ----
  function initCursor() {
    if (window.innerWidth < 768) return;

    const dot = document.querySelector('.cursor-dot');
    const outline = document.querySelector('.cursor-outline');
    if (!dot || !outline) return;

    let mouseX = window.innerWidth / 2;
    let mouseY = window.innerHeight / 2;
    let outlineX = mouseX;
    let outlineY = mouseY;

    document.addEventListener('mousemove', (e) => {
      mouseX = e.clientX;
      mouseY = e.clientY;
      dot.style.left = mouseX + 'px';
      dot.style.top = mouseY + 'px';
    });

    function animateOutline() {
      outlineX += (mouseX - outlineX) * 0.15;
      outlineY += (mouseY - outlineY) * 0.15;
      outline.style.left = outlineX + 'px';
      outline.style.top = outlineY + 'px';
      requestAnimationFrame(animateOutline);
    }
    animateOutline();

    // Hover effect on interactive elements
    const hoverTargets = document.querySelectorAll('a, button, .holo-card, .tech-item, [data-magnetic]');
    hoverTargets.forEach(el => {
      el.addEventListener('mouseenter', () => {
        dot.classList.add('hovering');
        outline.classList.add('hovering');
      });
      el.addEventListener('mouseleave', () => {
        dot.classList.remove('hovering');
        outline.classList.remove('hovering');
      });
    });
  }

  // ---- Mouse Trail ----
  function initTrail() {
    if (window.innerWidth < 768) return;

    const trails = document.querySelectorAll('.trail');
    if (!trails.length) return;

    let mouseX = 0;
    let mouseY = 0;
    const positions = Array.from(trails).map(() => ({ x: 0, y: 0 }));

    document.addEventListener('mousemove', (e) => {
      mouseX = e.clientX;
      mouseY = e.clientY;
    });

    function animate() {
      let x = mouseX;
      let y = mouseY;

      trails.forEach((trail, i) => {
        const pos = positions[i];
        const speed = 0.35 - i * 0.02;
        pos.x += (x - pos.x) * speed;
        pos.y += (y - pos.y) * speed;

        trail.style.left = pos.x + 'px';
        trail.style.top = pos.y + 'px';
        
        const opacity = (1 - i / trails.length) * 0.3;
        const size = (1 - i / trails.length) * 8;
        trail.style.opacity = opacity;
        trail.style.width = size + 'px';
        trail.style.height = size + 'px';

        x = pos.x;
        y = pos.y;
      });

      requestAnimationFrame(animate);
    }
    animate();
  }

  // ---- Sticky Navbar on Scroll ----
  function initNavbar() {
    const navbar = document.getElementById('navbar');
    const marquee = document.querySelector('.marquee-container');
    if (!navbar) return;

    let lastScroll = 0;

    window.addEventListener('scroll', () => {
      const currentScroll = window.pageYOffset;

      if (currentScroll > 100) {
        navbar.classList.add('scrolled');
        if (marquee) marquee.style.transform = 'translateY(-100%)';
      } else {
        navbar.classList.remove('scrolled');
        if (marquee) marquee.style.transform = 'translateY(0)';
      }

      lastScroll = currentScroll;
    }, { passive: true });
  }

  // ---- Mobile Nav Toggle ----
  function initMobileNav() {
    const toggle = document.getElementById('navToggle');
    const links = document.querySelector('.nav-links');
    if (!toggle || !links) return;

    toggle.addEventListener('click', () => {
      toggle.classList.toggle('active');
      links.classList.toggle('open');
    });

    links.querySelectorAll('a').forEach(link => {
      link.addEventListener('click', () => {
        toggle.classList.remove('active');
        links.classList.remove('open');
      });
    });
  }

  // ---- Holographic Card Glow Follow ----
  function initHoloCards() {
    const cards = document.querySelectorAll('.holo-card');
    
    cards.forEach(card => {
      const glow = card.querySelector('.holo-card-glow');
      if (!glow) return;

      card.addEventListener('mousemove', (e) => {
        const rect = card.getBoundingClientRect();
        const x = ((e.clientX - rect.left) / rect.width) * 100;
        const y = ((e.clientY - rect.top) / rect.height) * 100;
        glow.style.setProperty('--mouse-x', x + '%');
        glow.style.setProperty('--mouse-y', y + '%');
      });
    });
  }

  // ---- 3D Tilt Effect ----
  function initTilt() {
    const items = document.querySelectorAll('[data-tilt]');
    
    items.forEach(item => {
      item.addEventListener('mousemove', (e) => {
        const rect = item.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const y = e.clientY - rect.top;
        const centerX = rect.width / 2;
        const centerY = rect.height / 2;
        const rotateX = (y - centerY) / 15;
        const rotateY = (centerX - x) / 15;

        item.style.transform = `perspective(1000px) rotateX(${rotateX}deg) rotateY(${rotateY}deg)`;
      });

      item.addEventListener('mouseleave', () => {
        item.style.transform = 'perspective(1000px) rotateX(0) rotateY(0)';
      });
    });
  }

  // ---- Magnetic Buttons ----
  function initMagnetic() {
    const magnets = document.querySelectorAll('[data-magnetic].btn');
    
    magnets.forEach(btn => {
      let rafId = null;
      
      btn.addEventListener('mousemove', (e) => {
        if (rafId) cancelAnimationFrame(rafId);
        rafId = requestAnimationFrame(() => {
          const rect = btn.getBoundingClientRect();
          const x = e.clientX - rect.left - rect.width / 2;
          const y = e.clientY - rect.top - rect.height / 2;
          btn.style.transform = `translate(${x * 0.3}px, ${y * 0.3}px)`;
        });
      });

      btn.addEventListener('mouseleave', () => {
        if (rafId) {
          cancelAnimationFrame(rafId);
          rafId = null;
        }
        btn.style.transform = 'translate(0, 0)';
      });
    });
  }

  // ---- Number Counter Animation ----
  function initCounters() {
    const counters = document.querySelectorAll('.stat-number');
    if (!counters.length) return;

    const observer = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          const target = parseInt(entry.target.dataset.count);
          animateCounter(entry.target, target);
          observer.unobserve(entry.target);
        }
      });
    }, { threshold: 0.5 });

    counters.forEach(counter => observer.observe(counter));
  }

  function animateCounter(element, target) {
    let current = 0;
    const duration = 2000;
    const startTime = performance.now();

    function update(currentTime) {
      const elapsed = currentTime - startTime;
      const progress = Math.min(elapsed / duration, 1);
      
      // Ease out cubic
      const eased = 1 - Math.pow(1 - progress, 3);
      current = Math.round(eased * target);
      element.textContent = current;

      if (progress < 1) {
        requestAnimationFrame(update);
      }
    }
    requestAnimationFrame(update);
  }

  // ---- Timeline Scroll Reveal ----
  function initTimeline() {
    const items = document.querySelectorAll('.timeline-item');
    if (!items.length) return;

    const observer = new IntersectionObserver((entries) => {
      entries.forEach((entry, index) => {
        if (entry.isIntersecting) {
          setTimeout(() => {
            entry.target.classList.add('visible');
          }, index * 150);
          observer.unobserve(entry.target);
        }
      });
    }, { threshold: 0.2 });

    items.forEach(item => observer.observe(item));
  }

  // ---- Section Reveal on Scroll ----
  function initReveal() {
    const sections = document.querySelectorAll('section');
    
    const observer = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          entry.target.style.opacity = '1';
          entry.target.style.transform = 'translateY(0)';
        }
      });
    }, { threshold: 0.1 });

    sections.forEach(section => {
      section.style.opacity = '0';
      section.style.transform = 'translateY(30px)';
      section.style.transition = 'all 0.8s cubic-bezier(0.4, 0, 0.2, 1)';
      observer.observe(section);
    });

    // Make hero always visible
    const hero = document.querySelector('.hero');
    if (hero) {
      hero.style.opacity = '1';
      hero.style.transform = 'none';
    }
  }

  // ---- Parallax on Scroll ----
  function initParallax() {
    const heroRight = document.querySelector('.hero-right');
    if (!heroRight) return;

    let ticking = false;
    window.addEventListener('scroll', () => {
      if (!ticking) {
        requestAnimationFrame(() => {
          const scrolled = window.pageYOffset;
          if (scrolled < window.innerHeight) {
            heroRight.style.transform = `translateY(${scrolled * 0.05}px)`;
          } else {
            heroRight.style.transform = '';
          }
        });
        ticking = true;
      }
    }, { passive: true });
  }

  // ---- Fetch Version from GitHub API ----
  function initVersion() {
    const badge = document.getElementById('badgeVersion');
    if (!badge) return;

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 3000);

    fetch('https://api.github.com/repos/NoNameLeGo/jp-media-viewer/releases/latest', {
      signal: controller.signal
    })
      .then(res => {
        clearTimeout(timeout);
        return res.json();
      })
      .then(data => {
        if (data.tag_name) {
          badge.textContent = `${data.tag_name} · AGPL-3.0`;
        } else {
          badge.textContent = 'AGPL-3.0';
        }
      })
      .catch(() => {
        badge.textContent = 'AGPL-3.0';
      });
  }

  // ---- Initialize All ----
  function init() {
    initWebGL();
    initCursor();
    initTrail();
    initNavbar();
    initMobileNav();
    initHoloCards();
    initTilt();
    initMagnetic();
    initCounters();
    initTimeline();
    initReveal();
    initParallax();
    initVersion();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
