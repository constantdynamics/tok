// Browser-spraakherkenning via de Web Speech API. Werkt in Chrome/Edge (desktop én
// Android). Gebruikt online herkenning, dus vereist internet + https (GitHub Pages = https).
const SR = window.SpeechRecognition || window.webkitSpeechRecognition;

export function speechSupported() {
  return !!SR;
}

// Maakt een dictation-controller.
//   onText(text)   – live tekst (vastgezette zinnen + lopende partial)
//   onCommit(text) – een afgeronde bullet (door het signaalwoord, bv. "tak")
//   onCommand(name)– een herkend spraakcommando
//   onState(on)    – luisteren aan/uit
//   onError(code)  – foutcode (bv. 'not-allowed', 'network')
//   cutWord        – signaalwoord dat het transcript-tot-nu-toe afkapt tot een bullet
//   commands       – [{ re: RegExp, name: string }] commandozinnen (verwijderd uit de tekst)
export function createDictation({
  onText, onCommit, onCommand, onState, onError,
  lang = 'nl-NL', cutWord = 'tak', commands = [],
}) {
  if (!SR) return null;

  const rec = new SR();
  rec.lang = lang;
  rec.continuous = true;
  rec.interimResults = true;

  let listening = false;
  let base = ''; // vastgezette tekst (finals) sinds de laatste start/reset/commit
  const cutRe = cutWord ? new RegExp(`\\b${cutWord}\\b`, 'i') : null;

  function processFinals() {
    // 1) commando's: detecteer en verwijder de commandozin uit de tekst
    for (const c of commands) {
      if (c.re.test(base)) {
        base = base.replace(c.re, ' ').replace(/\s+/g, ' ').trim();
        onCommand?.(c.name);
      }
    }
    // 2) signaalwoord: alles vóór elk voorkomen wordt een aparte bullet
    if (cutRe) {
      while (cutRe.test(base)) {
        const idx = base.search(cutRe);
        const before = base.slice(0, idx).trim();
        const after = base.slice(idx).replace(cutRe, '').trim();
        if (before) onCommit?.(before);
        base = after;
      }
    }
  }

  rec.onresult = (e) => {
    let interim = '';
    let gotFinal = false;
    for (let i = e.resultIndex; i < e.results.length; i++) {
      const r = e.results[i];
      if (r.isFinal) { base = (base + ' ' + r[0].transcript).trim(); gotFinal = true; }
      else interim += r[0].transcript;
    }
    if (gotFinal) processFinals();
    onText?.((base + ' ' + interim).trim());
  };

  rec.onerror = (e) => {
    if (e.error === 'no-speech' || e.error === 'aborted') return;
    onError?.(e.error);
    if (e.error === 'not-allowed' || e.error === 'service-not-allowed') {
      listening = false;
      onState?.(false);
    }
  };

  rec.onend = () => {
    if (listening) {
      try { rec.start(); } catch (_) {}
    } else {
      onState?.(false);
    }
  };

  return {
    start(initial = '') {
      base = initial ? initial.trim() : '';
      listening = true;
      try { rec.start(); onState?.(true); } catch (_) {}
    },
    stop() {
      listening = false;
      try { rec.stop(); } catch (_) {}
      onState?.(false);
    },
    reset() { base = ''; },
    isListening() { return listening; },
  };
}
