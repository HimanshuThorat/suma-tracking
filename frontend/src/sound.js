// Step 40: a short bell for a successful sign-in, made in the browser (no sound file needed).
// Browsers allow sound only after a click or key press; pressing "Sign in" counts.
let ctx = null

export function playBell() {
  try {
    const AC = window.AudioContext || window.webkitAudioContext
    if (!AC) return
    ctx = ctx || new AC()
    if (ctx.state === 'suspended') ctx.resume()
    const now = ctx.currentTime
    const master = ctx.createGain()
    master.gain.value = 0.22
    master.connect(ctx.destination)
    // a bell = a few partials that fade at different speeds
    const partials = [[880, 1, 1.9], [1760, 0.45, 1.2], [2637, 0.28, 0.8], [3520, 0.14, 0.5], [1318.5, 0.35, 1.5]]
    partials.forEach(([freq, level, decay], i) => {
      const osc = ctx.createOscillator()
      const g = ctx.createGain()
      osc.type = 'sine'
      osc.frequency.value = freq
      g.gain.setValueAtTime(0.0001, now)
      g.gain.exponentialRampToValueAtTime(level, now + 0.008 + i * 0.002)
      g.gain.exponentialRampToValueAtTime(0.0001, now + decay)
      osc.connect(g)
      g.connect(master)
      osc.start(now)
      osc.stop(now + decay + 0.05)
    })
  } catch {
    /* no sound is fine */
  }
}