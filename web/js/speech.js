// Browser-spraakherkenning via de Web Speech API. Werkt in Chrome/Edge (desktop én
// Android). Gebruikt online herkenning, dus vereist internet + https (GitHub Pages = https).
const SR = window.SpeechRecognition || window.webkitSpeechRecognition;

export function speechSupported() {
  return !!SR;
}

function escapeRe(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

// Gesproken leestekens -> echte tekens. "dubbele punt"/"puntkomma" vóór "punt".
function applyPunctuation(text) {
  let t = text;
  const rules = [
    [/\bnieuwe alinea\b/gi, '\n\n'],
    [/\bnieuwe regel\b/gi, '\n'],
    [/\bvraagteken\b/gi, '?'],
    [/\buitroepteken\b/gi, '!'],
    [/\bpuntkomma\b/gi, ';'],
    [/\bdubbele punt\b/gi, ':'],
    [/\bkomma\b/gi, ','],
    [/\bpunt\b/gi, '.'],
  ];
  for (const [re, rep] of rules) t = t.replace(re, rep);
  t = t.replace(/\s+([.,!?;:])/g, '$1');     // geen spatie vóór een leesteken
  t = t.replace(/[ \t]*\n[ \t]*/g, '\n');    // spaties rond nieuwe regels weg
  t = t.replace(/[ \t]{2,}/g, ' ');
  return t.trim();
}

// Hoofdletter aan het begin en na . ! ? of een nieuwe regel.
function capitalizeSentences(text) {
  return text.replace(/(^|[.!?]\s+|\n[ \t]*)([a-zà-öø-ÿ])/g, (m, p, c) => p + c.toUpperCase());
}

// Maakt een dictation-controller.
//   onText/onCommit/onCommand/onState/onError – callbacks
//   cutWords  – lijst signaalwoorden/-zinnen die de tekst-tot-nu-toe afkappen tot bullet
//   commands  – [{ re, name }] commandozinnen (verwijderd uit de tekst)
//   punctuation – gesproken leestekens omzetten + hoofdletters (default aan)
export function createDictation({
  onText, onCommit, onCommand, onState, onError,
  lang = 'nl-NL', cutWords = ['tak'], commands = [], punctuation = true,
}) {
  if (!SR) return null;

  const rec = new SR();
  rec.lang = lang;
  rec.continuous = true;
  rec.interimResults = true;

  let listening = false;
  let base = '';
  const cutRe = (cutWords && cutWords.length)
    ? new RegExp(`\\b(?:${cutWords.map(escapeRe).join('|')})\\b`, 'i')
    : null;

  function processFinals() {
    if (punctuation) base = capitalizeSentences(applyPunctuation(base));
    // 1) commando's: detecteer en verwijder de commandozin
    for (const c of commands) {
      if (c.re.test(base)) {
        base = base.replace(c.re, ' ').replace(/\s+/g, ' ').trim();
        onCommand?.(c.name);
      }
    }
    // 2) signaalwoorden: alles vóór elk voorkomen wordt een aparte bullet
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
