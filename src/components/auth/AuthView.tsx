import React, { useEffect, useState } from 'react';
import { Capacitor } from '@capacitor/core';
import { Browser } from '@capacitor/browser';
import { supabase, SUPABASE_URL, SUPABASE_ANON_KEY, NATIVE_AUTH_REDIRECT } from '../../lib/supabase';

type OAuthProvider = 'google';

// Official brand marks (sign-in buttons should use the provider's own logo)
const GoogleLogo = () => (
  <svg viewBox="0 0 48 48" className="w-4 h-4" aria-hidden="true">
    <path fill="#FFC107" d="M43.611 20.083H42V20H24v8h11.303c-1.649 4.657-6.08 8-11.303 8-6.627 0-12-5.373-12-12s5.373-12 12-12c3.059 0 5.842 1.154 7.961 3.039l5.657-5.657C34.046 6.053 29.268 4 24 4 12.955 4 4 12.955 4 24s8.955 20 20 20 20-8.955 20-20c0-1.341-.138-2.65-.389-3.917z" />
    <path fill="#FF3D00" d="M6.306 14.691l6.571 4.819C14.655 15.108 18.961 12 24 12c3.059 0 5.842 1.154 7.961 3.039l5.657-5.657C34.046 6.053 29.268 4 24 4 16.318 4 9.656 8.337 6.306 14.691z" />
    <path fill="#4CAF50" d="M24 44c5.166 0 9.86-1.977 13.409-5.192l-6.19-5.238A11.91 11.91 0 0 1 24 36c-5.202 0-9.619-3.317-11.283-7.946l-6.522 5.025C9.505 39.556 16.227 44 24 44z" />
    <path fill="#1976D2" d="M43.611 20.083H42V20H24v8h11.303a12.04 12.04 0 0 1-4.087 5.571l.003-.002 6.19 5.238C36.971 39.205 44 34 44 24c0-1.341-.138-2.65-.389-3.917z" />
  </svg>
);

const OAUTH_BUTTONS: Array<{ provider: OAuthProvider; label: string; Logo: () => React.ReactElement }> = [
  { provider: 'google', label: 'Google', Logo: GoogleLogo }
];
import { 
  Mail, 
  Lock, 
  User as UserIcon, 
  Eye, 
  EyeOff, 
  Sparkles, 
  BrainCircuit, 
  CheckCircle2, 
  AlertCircle,
  ArrowRight,
  ArrowLeft,
  Zap,
  ShieldCheck,
  BookOpen,
  GitFork,
  HelpCircle,
  Users
} from 'lucide-react';
import { useStudy } from '../../context/StudyContext';

export function AuthView() {
  const { 
    authMode, 
    closeAuthModal, 
    login, 
    signup,
    setActiveTab
  } = useStudy();

  const [activeTabMode, setActiveTabMode] = useState<'login' | 'signup'>(authMode || 'login');
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  // Only providers switched on in Supabase get a button, so none is ever a dead end
  const [enabledProviders, setEnabledProviders] = useState<OAuthProvider[]>([]);

  useEffect(() => {
    fetch(`${SUPABASE_URL}/auth/v1/settings`, { headers: { apikey: SUPABASE_ANON_KEY } })
      .then(res => res.json())
      .then(settings => setEnabledProviders(OAUTH_BUTTONS.map(b => b.provider).filter(p => settings?.external?.[p])))
      .catch(() => setEnabledProviders([]));

    // Returning from Google with an error (e.g. the user cancelled): show it, then clean the URL
    const params = new URLSearchParams(window.location.hash.slice(1) || window.location.search);
    if (params.get('error')) {
      setErrorMessage(params.get('error') === 'access_denied'
        ? 'Bạn đã hủy đăng nhập. Hãy thử lại hoặc dùng email.'
        : `Đăng nhập thất bại: ${params.get('error_description') || params.get('error')}`);
      window.history.replaceState(null, '', window.location.pathname);
    }
  }, []);

  const handleOAuth = async (provider: OAuthProvider) => {
    setErrorMessage(null);
    setLoading(true);
    // Web: the page leaves for the provider and comes back signed in (Supabase reads the session from the URL).
    // Android: Google refuses OAuth inside a WebView, so use the system browser and return via the
    // com.studymind.app://auth/callback deep link (handled in StudyContext).
    const native = Capacitor.isNativePlatform();
    const { data, error } = await supabase.auth.signInWithOAuth({
      provider,
      options: { redirectTo: native ? NATIVE_AUTH_REDIRECT : window.location.origin, skipBrowserRedirect: native }
    });
    if (!error && native && data.url) {
      await Browser.open({ url: data.url });
      setLoading(false);
      return;
    }
    if (error) {
      setErrorMessage(`Không thể kết nối Google: ${error.message}`);
      setLoading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);
    setLoading(true);

    try {
      if (activeTabMode === 'login') {
        const res = await login(email, password);
        if (res.success) {
          closeAuthModal();
        } else {
          setErrorMessage(res.error || "Email hoặc mật khẩu không chính xác.");
        }
      } else {
        const res = await signup(fullName, email, password);
        if (res.success) {
          closeAuthModal();
        } else {
          setErrorMessage(res.error || "Đăng ký tài khoản thất bại.");
        }
      }
    } catch (err: any) {
      setErrorMessage(err.message || "Đã xảy ra lỗi hệ thống.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div 
      className="min-h-screen w-full flex items-center justify-center p-4 md:p-8 font-sans relative overflow-hidden"
      style={{
        backgroundImage: "url('/clouds-pattern.png')",
        backgroundRepeat: 'repeat',
        backgroundPosition: 'top left'
      }}
    >
      {/* Dark Ambient Overlay over Stylized Clouds Pattern */}
      <div className="absolute inset-0 bg-slate-900/40 backdrop-blur-[2px] pointer-events-none" />

      <div className="max-w-5xl w-full bg-white/95 backdrop-blur-xl rounded-3xl shadow-2xl overflow-hidden border border-slate-200/90 grid grid-cols-1 lg:grid-cols-12 min-h-[640px] relative z-10 animate-in fade-in zoom-in-95">
        
        {/* Left Side: Brand & Feature Showcase (Clean Teal Hero Banner) */}
        <div className="lg:col-span-5 bg-gradient-to-br from-[#0F766E] via-[#0D645E] to-[#115E59] p-8 md:p-10 text-white flex flex-col justify-between relative overflow-hidden">
          {/* Ambient Glowing Orbs */}
          <div className="absolute -top-16 -left-16 w-48 h-48 bg-teal-300/20 rounded-full blur-3xl pointer-events-none" />
          <div className="absolute -bottom-20 -right-20 w-64 h-64 bg-emerald-400/20 rounded-full blur-3xl pointer-events-none" />

          {/* Top Brand Logo */}
          <div className="relative z-10">
            <div className="flex items-center gap-3">
              <div className="w-11 h-11 rounded-2xl bg-white/20 backdrop-blur-md flex items-center justify-center text-white border border-white/30 shadow-lg">
                <BrainCircuit className="w-6 h-6" />
              </div>
              <div>
                <h1 className="text-xl font-black tracking-tight text-white leading-none">
                  StudyMind <span className="text-teal-200">AI</span>
                </h1>
                <p className="text-xs text-teal-100/90 font-medium mt-1">
                  Trợ lý học tập cá nhân hóa
                </p>
              </div>
            </div>

            {/* Headline */}
            <div className="mt-8 space-y-3">
              <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-white/15 text-teal-100 text-xs font-semibold backdrop-blur-md border border-white/20 shadow-xs">
                <Sparkles className="w-3.5 h-3.5 text-teal-300" />
                Công nghệ AI Thế hệ Mới
              </span>
              <h2 className="text-2xl md:text-3xl font-bold leading-tight text-white drop-shadow-xs">
                Biến tài liệu dài thành kiến thức ghi nhớ nhanh
              </h2>
              <p className="text-xs md:text-sm text-teal-100/90 leading-relaxed">
                Tự động trích xuất Tóm tắt, Sơ đồ tư duy, Thẻ ghi nhớ và Bộ câu hỏi trắc nghiệm thông minh từ PDF, Video hoặc bài viết Web.
              </p>
            </div>
          </div>

          {/* Features List */}
          <div className="my-8 space-y-3.5 relative z-10">
            <div className="flex items-start gap-3">
              <div className="p-1.5 rounded-lg bg-white/15 backdrop-blur-md text-teal-200 shrink-0 mt-0.5 border border-white/10">
                <Zap className="w-4 h-4" />
              </div>
              <div>
                <h4 className="text-xs font-bold text-white">Xử lý siêu tốc với Groq LPU & Gemini 3.6</h4>
                <p className="text-[11px] text-teal-100/80">Phân tích chuyên sâu văn bản & đa phương tiện chỉ trong vài giây.</p>
              </div>
            </div>

            <div className="flex items-start gap-3">
              <div className="p-1.5 rounded-lg bg-white/15 backdrop-blur-md text-teal-200 shrink-0 mt-0.5 border border-white/10">
                <GitFork className="w-4 h-4" />
              </div>
              <div>
                <h4 className="text-xs font-bold text-white">Tự động dựng Sơ đồ tư duy & Flashcards</h4>
                <p className="text-[11px] text-teal-100/80">Dễ dàng ôn tập theo phương pháp Spaced Repetition khoa học.</p>
              </div>
            </div>

            <div className="flex items-start gap-3">
              <div className="p-1.5 rounded-lg bg-white/15 backdrop-blur-md text-teal-200 shrink-0 mt-0.5 border border-white/10">
                <ShieldCheck className="w-4 h-4" />
              </div>
              <div>
                <h4 className="text-xs font-bold text-white">Đồng bộ đám mây với Supabase</h4>
                <p className="text-[11px] text-teal-100/80">Lưu trữ dữ liệu học tập an toàn, truy cập mọi lúc mọi nơi.</p>
              </div>
            </div>
          </div>

          {/* Bottom Back Button & Stats */}
          <div className="pt-6 border-t border-white/20 flex items-center justify-end relative z-10">
            <div className="flex items-center gap-1.5 text-[11px] text-teal-200/95 font-semibold">
              <Users className="w-3.5 h-3.5" />
              <span>10.000+ Học viên</span>
            </div>
          </div>
        </div>

        {/* Right Side: Form Area */}
        <div className="lg:col-span-7 p-8 md:p-12 flex flex-col justify-center bg-white/95">
          
          <div className="max-w-md mx-auto w-full space-y-6">

            {/* Form Header Tabs */}
            <div>
              <div className="flex items-center justify-between mb-2">
                <h3 className="text-xl font-extrabold text-slate-900">
                  {activeTabMode === 'login' ? 'Chào mừng bạn trở lại' : 'Tạo tài khoản mới'}
                </h3>
              </div>
              <p className="text-xs text-slate-500">
                {activeTabMode === 'login' 
                  ? 'Vui lòng nhập thông tin tài khoản để tiếp tục tiến trình học tập.' 
                  : 'Đăng ký ngay để trải nghiệm đầy đủ các tính năng AI hỗ trợ học tập.'}
              </p>

              {/* Mode Switch Pills (100% Flush Border iOS Sliding Control) */}
              <div className="relative flex p-0 bg-slate-200/80 backdrop-blur-md rounded-2xl mt-4 text-xs font-bold text-slate-600 border border-slate-300/80 shadow-inner overflow-hidden">
                {/* 100% Flush Active Sliding Pill Background */}
                <div 
                  className={`absolute top-0 bottom-0 left-0 w-1/2 bg-white shadow-[0_2px_10px_rgba(0,0,0,0.1)] transition-transform duration-300 ease-[cubic-bezier(0.16,1,0.3,1)] ${
                    activeTabMode === 'login' ? 'translate-x-0' : 'translate-x-full'
                  }`} 
                />

                {/* Tab 1: Đăng nhập */}
                <button
                  type="button"
                  onClick={() => { setActiveTabMode('login'); setErrorMessage(null); }}
                  className={`relative z-10 flex-1 py-3 text-center transition-colors duration-200 ${
                    activeTabMode === 'login' 
                      ? 'text-[#0F766E] font-black' 
                      : 'text-slate-500 hover:text-slate-900 font-semibold'
                  }`}
                >
                  Đăng nhập
                </button>

                {/* Tab 2: Đăng ký tài khoản */}
                <button
                  type="button"
                  onClick={() => { setActiveTabMode('signup'); setErrorMessage(null); }}
                  className={`relative z-10 flex-1 py-3 text-center transition-colors duration-200 ${
                    activeTabMode === 'signup' 
                      ? 'text-[#0F766E] font-black' 
                      : 'text-slate-500 hover:text-slate-900 font-semibold'
                  }`}
                >
                  Đăng ký tài khoản
                </button>
              </div>
            </div>

            {/* Error Message Alert */}
            {errorMessage && (
              <div className="bg-rose-50 border border-rose-200 text-rose-700 p-3.5 rounded-2xl text-xs flex items-center gap-2.5 animate-in fade-in">
                <AlertCircle className="w-4 h-4 shrink-0 text-rose-600" />
                <span className="font-medium">{errorMessage}</span>
              </div>
            )}

            {/* Social sign-in */}
            {enabledProviders.length > 0 && (
              <div className="space-y-4">
                <div className={`grid gap-2 ${enabledProviders.length > 1 ? 'grid-cols-2' : 'grid-cols-1'}`}>
                  {OAUTH_BUTTONS.filter(b => enabledProviders.includes(b.provider)).map(({ provider, label, Logo }) => (
                    <button
                      key={provider}
                      type="button"
                      onClick={() => handleOAuth(provider)}
                      disabled={loading}
                      className="w-full py-3 px-4 bg-white border border-slate-200 hover:bg-slate-50 hover:border-slate-300 rounded-xl text-xs font-bold text-slate-700 shadow-xs transition-all flex items-center justify-center gap-2 disabled:opacity-60"
                    >
                      <Logo />
                      <span>{label}</span>
                    </button>
                  ))}
                </div>
                <div className="flex items-center gap-3 text-xs text-slate-400">
                  <div className="flex-1 h-px bg-slate-200" />
                  <span className="font-medium text-[11px]">HOẶC DÙNG EMAIL</span>
                  <div className="flex-1 h-px bg-slate-200" />
                </div>
              </div>
            )}

            {/* Form */}
            <form onSubmit={handleSubmit} className="space-y-4">
              
              {/* Full Name input for Signup */}
              {activeTabMode === 'signup' && (
                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1.5">Họ và tên</label>
                  <div className="relative">
                    <UserIcon className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
                    <input
                      type="text"
                      required
                      value={fullName}
                      onChange={(e) => setFullName(e.target.value)}
                      placeholder="Nguyễn Văn A"
                      className="w-full pl-10 pr-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-xs font-medium text-slate-900 focus:bg-white focus:outline-none focus:ring-2 focus:ring-[#0F766E]/20 focus:border-[#0F766E] transition-all"
                    />
                  </div>
                </div>
              )}

              {/* Email Input */}
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1.5">Địa chỉ Email</label>
                <div className="relative">
                  <Mail className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
                  <input
                    type="email"
                    required
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="user@example.com"
                    className="w-full pl-10 pr-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-xs font-medium text-slate-900 focus:bg-white focus:outline-none focus:ring-2 focus:ring-[#0F766E]/20 focus:border-[#0F766E] transition-all"
                  />
                </div>
              </div>

              {/* Password Input */}
              <div>
                <div className="flex items-center justify-between mb-1.5">
                  <label className="block text-xs font-bold text-slate-700">Mật khẩu</label>
                  {activeTabMode === 'login' && (
                    <span className="text-[11px] font-semibold text-[#0F766E] hover:underline cursor-pointer">
                      Quên mật khẩu?
                    </span>
                  )}
                </div>
                <div className="relative">
                  <Lock className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
                  <input
                    type={showPassword ? 'text' : 'password'}
                    required
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="••••••••"
                    className="w-full pl-10 pr-10 py-3 bg-slate-50 border border-slate-200 rounded-xl text-xs font-medium text-slate-900 focus:bg-white focus:outline-none focus:ring-2 focus:ring-[#0F766E]/20 focus:border-[#0F766E] transition-all"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-3.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 p-1"
                  >
                    {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              {/* Submit Button */}
              <button
                type="submit"
                disabled={loading}
                className="w-full mt-2 bg-[#0F766E] hover:bg-[#0D645E] text-white text-xs font-bold py-3.5 px-4 rounded-xl shadow-md transition-all flex items-center justify-center gap-2 group"
              >
                {loading ? (
                  <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                ) : (
                  <>
                    <span>{activeTabMode === 'login' ? 'Đăng nhập vào tài khoản' : 'Đăng ký tài khoản ngay'}</span>
                    <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
                  </>
                )}
              </button>
            </form>

            {/* Footer Notice */}
            <div className="pt-4 border-t border-slate-100 text-center">
              <p className="text-[11px] text-slate-400">
                Bằng cách đăng nhập, bạn đồng ý với{' '}
                <a href="/privacy.html" target="_blank" rel="noopener" className="text-[#0F766E] font-semibold hover:underline">
                  Chính sách quyền riêng tư
                </a>{' '}
                của StudyMind AI.
              </p>
            </div>

          </div>

        </div>

      </div>
    </div>
  );
}
