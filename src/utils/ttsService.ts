// Text-to-Speech (TTS) Service using Web Speech API (supported natively in all modern Android WebViews and browsers)

type TTSListener = (state: { speaking: boolean; paused: boolean; currentText: string }) => void;

class TTSService {
  private synth: SpeechSynthesis | null = null;
  private currentUtterance: SpeechSynthesisUtterance | null = null;
  private speaking: boolean = false;
  private paused: boolean = false;
  private currentText: string = '';
  private listeners: Set<TTSListener> = new Set();
  private rate: number = 1.0;

  constructor() {
    if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
      this.synth = window.speechSynthesis;
    }
  }

  isSupported(): boolean {
    return !!this.synth;
  }

  subscribe(listener: TTSListener): () => void {
    this.listeners.add(listener);
    listener({ speaking: this.speaking, paused: this.paused, currentText: this.currentText });
    return () => this.listeners.delete(listener);
  }

  private notify() {
    this.listeners.forEach((l) =>
      l({ speaking: this.speaking, paused: this.paused, currentText: this.currentText })
    );
  }

  setRate(rate: number) {
    this.rate = rate;
  }

  getRate(): number {
    return this.rate;
  }

  stop() {
    if (!this.synth) return;
    this.synth.cancel();
    this.speaking = false;
    this.paused = false;
    this.currentText = '';
    this.notify();
  }

  pause() {
    if (!this.synth || !this.speaking) return;
    this.synth.pause();
    this.paused = true;
    this.notify();
  }

  resume() {
    if (!this.synth || !this.paused) return;
    this.synth.resume();
    this.paused = false;
    this.notify();
  }

  speak(text: string, lang: string = 'vi-VN', onEndCallback?: () => void) {
    if (!this.synth) {
      console.warn('SpeechSynthesis is not supported on this platform');
      return;
    }

    this.stop();

    const cleanText = text.replace(/[*#_~`]/g, '').trim();
    if (!cleanText) return;

    this.currentText = cleanText;
    this.speaking = true;
    this.paused = false;
    this.notify();

    const utterance = new SpeechSynthesisUtterance(cleanText);
    utterance.lang = lang;
    utterance.rate = this.rate;

    // Pick Vietnamese or English voice if available
    const voices = this.synth.getVoices();
    const voice = voices.find((v) => v.lang.startsWith(lang.slice(0, 2))) || null;
    if (voice) {
      utterance.voice = voice;
    }

    utterance.onend = () => {
      this.speaking = false;
      this.paused = false;
      this.currentText = '';
      this.notify();
      if (onEndCallback) onEndCallback();
    };

    utterance.onerror = (e) => {
      console.warn('TTS error:', e);
      this.speaking = false;
      this.paused = false;
      this.notify();
    };

    this.currentUtterance = utterance;
    this.synth.speak(utterance);
  }
}

export const ttsService = new TTSService();
