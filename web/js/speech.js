// Browser-spraakherkenning via de Web Speech API. Werkt in Chrome/Edge (desktop én
// Android). Gebruikt online herkenning, dus vereist internet + https (GitHub Pages = https).
const SR = window.SpeechRecognition || window.webkitSpeechRecognition;

export function speechSupported() {
  return !!SR;
}

// Maakt een dictation-controller. Callbacks:
//   onText(text)  – live tekst (vastgezette zinnen + lopende partial)
//   onState(on)   – luisteren aan/uit
//   onError(code) – foutcode (bv. 'not-allowed', 'network')
export function createDictation({ onText, onState, onError, lang = 'nl-NL' }) {
  if (!SR) return null;

  const rec = new SR();
  rec.lang = lang;
  rec.continuous = true;
  rec.interimResults = true;

  let listening = false;
  let base = ''; // vastgezette tekst (finals) sinds de laatste start/reset

  rec.onresult = (e) => {
    let interim = '';
    for (let i = e.resultIndex; i < e.results.length; i++) {
      const r = e.results[i];
      if (r.isFinal) base = (base + ' ' + r[0].transcript).trim();
      else interim += r[0].transcript;
    }
    onText?.((base + ' ' + interim).trim());
  };

  rec.onerror = (e) => {
    if (e.error === 'no-speech' || e.error === 'aborted') return; // niets aan de hand
    onError?.(e.error);
    if (e.error === 'not-allowed' || e.error === 'service-not-allowed') {
      listening = false;
      onState?.(false);
    }
  };

  rec.onend = () => {
    // Chrome stopt 'continuous' na stilte/time-out; herstart zolang we willen luisteren.
    if (listening) {
      try { rec.start(); } catch (_) { /* nog bezig met afsluiten */ }
    } else {
      onState?.(false);
    }
  };

  return {
    start(initial = '') {
      base = initial ? initial.trim() : '';
      listening = true;
      try { rec.start(); onState?.(true); } catch (_) { /* al gestart */ }
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
