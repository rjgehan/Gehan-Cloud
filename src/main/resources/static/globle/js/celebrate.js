// A short burst of confetti for a win. One throwaway canvas, removed when the
// pieces have fallen; nothing runs before or after. Skipped entirely when the
// viewer asks for reduced motion (the globe's green fill is celebration enough).

const COLORS = ["#22c55e", "#86efac", "#facc15", "#5aa9ff", "#ffffff", "#fb923c", "#a06bff"];
const DURATION_MS = 2600;
const TAU = Math.PI * 2;

export function celebrate({ reducedMotion = false, origin } = {}) {
    if (reducedMotion || typeof document === "undefined") return;
    const canvas = document.createElement("canvas");
    canvas.className = "gl-confetti";
    canvas.setAttribute("aria-hidden", "true");
    document.body.append(canvas);

    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const w = window.innerWidth;
    const h = window.innerHeight;
    canvas.width = Math.round(w * dpr);
    canvas.height = Math.round(h * dpr);
    const ctx = canvas.getContext("2d");
    ctx.scale(dpr, dpr);

    const ox = origin ? origin.x : w / 2;
    const oy = origin ? origin.y : h * 0.4;
    const pieces = Array.from({ length: Math.min(160, Math.round(w / 4)) }, () => {
        const angle = -Math.PI / 2 + (Math.random() - 0.5) * 1.9;
        const speed = 6 + Math.random() * 9;
        return {
            x: ox + (Math.random() - 0.5) * 40,
            y: oy + (Math.random() - 0.5) * 20,
            vx: Math.cos(angle) * speed,
            vy: Math.sin(angle) * speed,
            size: 5 + Math.random() * 6,
            rot: Math.random() * TAU,
            vr: (Math.random() - 0.5) * 0.35,
            color: COLORS[Math.floor(Math.random() * COLORS.length)],
            round: Math.random() < 0.35,
        };
    });

    const start = performance.now();
    let last = start;
    const frame = (now) => {
        // Physics in 60 fps steps so a 120 Hz screen does not double the speed.
        const step = Math.min(3, (now - last) / 16.67);
        last = now;
        const t = now - start;
        ctx.clearRect(0, 0, w, h);
        ctx.globalAlpha = Math.max(0, 1 - Math.max(0, t - DURATION_MS * 0.6) / (DURATION_MS * 0.4));
        for (const p of pieces) {
            p.vy += 0.32 * step;
            p.vx *= Math.pow(0.985, step);
            p.x += p.vx * step;
            p.y += p.vy * step;
            p.rot += p.vr * step;
            ctx.save();
            ctx.translate(p.x, p.y);
            ctx.rotate(p.rot);
            ctx.fillStyle = p.color;
            if (p.round) {
                ctx.beginPath();
                ctx.arc(0, 0, p.size / 2.4, 0, TAU);
                ctx.fill();
            } else {
                ctx.fillRect(-p.size / 2, -p.size / 4, p.size, p.size / 2);
            }
            ctx.restore();
        }
        if (t < DURATION_MS) {
            requestAnimationFrame(frame);
        } else {
            canvas.remove();
        }
    };
    requestAnimationFrame(frame);
}
