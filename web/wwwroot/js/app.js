/* FlowSense — app.js */
(function () {
    'use strict';

    /* ── Theme management ────────────────────────────────── */
    const THEME_KEY = 'flowsense-theme';
    const THEMES = ['system', 'light', 'dark'];
    const html = document.documentElement;

    function getTheme() {
        return localStorage.getItem(THEME_KEY) || 'system';
    }

    function setTheme(theme) {
        html.setAttribute('data-theme', theme);
        localStorage.setItem(THEME_KEY, theme);
    }

    function cycleTheme() {
        const current = getTheme();
        const next = THEMES[(THEMES.indexOf(current) + 1) % THEMES.length];
        setTheme(next);
        announceTheme(next);
    }

    function announceTheme(theme) {
        const labels = { system: 'System (auto)', light: 'Light', dark: 'Dark' };
        const btn = document.getElementById('themeToggle');
        if (btn) {
            btn.title = `Theme: ${labels[theme]} — click to change`;
            btn.setAttribute('aria-label', `Theme: ${labels[theme]}`);
        }
    }

    // Apply saved theme immediately
    const savedTheme = getTheme();
    setTheme(savedTheme);

    /* ── Navbar scroll shadow ────────────────────────────── */
    function handleNavbarScroll() {
        const navbar = document.getElementById('navbar');
        if (!navbar) return;
        if (window.scrollY > 12) {
            navbar.classList.add('scrolled');
        } else {
            navbar.classList.remove('scrolled');
        }
    }

    /* ── Mobile hamburger ────────────────────────────────── */
    function initHamburger() {
        const btn = document.getElementById('hamburger');
        const links = document.getElementById('navLinks');
        if (!btn || !links) return;

        btn.addEventListener('click', () => {
            const open = links.classList.toggle('open');
            btn.setAttribute('aria-expanded', open);
            const spans = btn.querySelectorAll('span');
            if (open) {
                spans[0].style.transform = 'translateY(7px) rotate(45deg)';
                spans[1].style.opacity = '0';
                spans[2].style.transform = 'translateY(-7px) rotate(-45deg)';
            } else {
                spans.forEach(s => { s.style.transform = ''; s.style.opacity = ''; });
            }
        });

        // Close on nav link click
        links.querySelectorAll('.nav-link').forEach(a => {
            a.addEventListener('click', () => {
                links.classList.remove('open');
                btn.setAttribute('aria-expanded', 'false');
                btn.querySelectorAll('span').forEach(s => { s.style.transform = ''; s.style.opacity = ''; });
            });
        });

        // Close on outside click
        document.addEventListener('click', e => {
            if (!btn.contains(e.target) && !links.contains(e.target)) {
                links.classList.remove('open');
            }
        });
    }

    /* ── Star rating accessibility ───────────────────────── */
    function initStarRating() {
        const container = document.getElementById('starRating');
        if (!container) return;

        const inputs = container.querySelectorAll('input[type=radio]');
        inputs.forEach(input => {
            input.addEventListener('change', () => {
                // Visual feedback handled purely by CSS
                container.setAttribute('data-value', input.value);
            });
        });
    }

    /* ── Smooth anchor scroll for hash links ─────────────── */
    function initSmoothScroll() {
        document.querySelectorAll('a[href^="#"]').forEach(a => {
            a.addEventListener('click', e => {
                const id = a.getAttribute('href').slice(1);
                const target = document.getElementById(id);
                if (target) {
                    e.preventDefault();
                    target.scrollIntoView({ behavior: 'smooth', block: 'start' });
                }
            });
        });
    }

    /* ── Intersection Observer — fade-in on scroll ────────── */
    function initFadeIn() {
        if (!window.IntersectionObserver) return;

        const style = document.createElement('style');
        style.textContent = `
            .fade-target {
                opacity: 0;
                transform: translateY(24px);
                transition: opacity .5s ease, transform .5s ease;
            }
            .fade-target.visible {
                opacity: 1;
                transform: none;
            }
        `;
        document.head.appendChild(style);

        const selectors = [
            '.feature-card',
            '.step',
            '.ai-mode',
            '.review-card',
            '.section-header',
            '.sg-card',
        ];
        const elements = document.querySelectorAll(selectors.join(','));

        const observer = new IntersectionObserver(entries => {
            entries.forEach((entry, i) => {
                if (entry.isIntersecting) {
                    setTimeout(() => entry.target.classList.add('visible'), i * 60);
                    observer.unobserve(entry.target);
                }
            });
        }, { threshold: 0.1 });

        elements.forEach(el => {
            el.classList.add('fade-target');
            observer.observe(el);
        });
    }

    /* ── TOC scroll-spy (policy & settings pages) ────────── */
    function initTocScrollSpy() {
        const toc = document.querySelector('.policy-toc nav');
        if (!toc) return;

        const links = Array.from(toc.querySelectorAll('a[href^="#"]'));
        if (!links.length) return;

        const sections = links
            .map(a => document.getElementById(a.getAttribute('href').slice(1)))
            .filter(Boolean);

        const navH = parseInt(getComputedStyle(document.documentElement)
            .getPropertyValue('--navbar-h')) || 64;

        function updateActive() {
            let current = sections[0];
            sections.forEach(sec => {
                if (sec.getBoundingClientRect().top <= navH + 32) current = sec;
            });
            links.forEach(a => {
                a.classList.toggle('active',
                    a.getAttribute('href') === '#' + (current ? current.id : ''));
            });
        }

        window.addEventListener('scroll', updateActive, { passive: true });
        updateActive();
    }

    /* ── Textarea character counter ──────────────────────── */
    function initCharCounter() {
        const textarea = document.getElementById('Input_Comment');
        if (!textarea) return;

        const counter = document.createElement('div');
        counter.style.cssText = 'font-size:.75rem;color:var(--text-muted);text-align:right;margin-top:4px;';
        counter.textContent = `0 / 1000`;
        textarea.parentNode.insertBefore(counter, textarea.nextSibling);

        textarea.addEventListener('input', () => {
            const len = textarea.value.length;
            counter.textContent = `${len} / 1000`;
            counter.style.color = len > 900 ? '#ef4444' : 'var(--text-muted)';
        });
    }

    /* ── Form submit loading state ───────────────────────── */
    function initFormLoading() {
        const form = document.querySelector('form[method="post"]');
        if (!form) return;
        form.addEventListener('submit', () => {
            const btn = form.querySelector('button[type=submit]');
            if (btn) {
                btn.disabled = true;
                btn.textContent = 'Submitting…';
            }
        });
    }

    /* ── Init ─────────────────────────────────────────────── */
    document.addEventListener('DOMContentLoaded', () => {
        // Theme toggle button
        const themeBtn = document.getElementById('themeToggle');
        if (themeBtn) {
            themeBtn.addEventListener('click', cycleTheme);
            announceTheme(getTheme());
        }

        window.addEventListener('scroll', handleNavbarScroll, { passive: true });
        handleNavbarScroll();

        initHamburger();
        initStarRating();
        initSmoothScroll();
        initFadeIn();
        initTocScrollSpy();
        initCharCounter();
        initFormLoading();
    });

})();
