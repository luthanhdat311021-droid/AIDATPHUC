import React, { useState } from 'react';
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

  const handleDemoLogin = async () => {
    setLoading(true);
    setErrorMessage(null);
    try {
      await login("demo@studymind.ai", "demo123456");
      closeAuthModal();
    } catch (err) {
      setErrorMessage("Không thể đăng nhập tài khoản mẫu.");
    } finally {
      setLoading(false);
    }
  };

  const handleBackToApp = () => {
    closeAuthModal();
    setActiveTab('dashboard');
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
          <div className="pt-6 border-t border-white/20 flex items-center justify-between relative z-10">
            <button
              onClick={handleBackToApp}
              className="inline-flex items-center gap-2 text-xs font-bold text-teal-100 hover:text-white hover:underline transition-all"
            >
              <ArrowLeft className="w-4 h-4" />
              <span>Quay lại ứng dụng</span>
            </button>

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
                <button
                  onClick={handleBackToApp}
                  className="lg:hidden text-xs text-slate-500 hover:text-slate-800 flex items-center gap-1"
                >
                  <ArrowLeft className="w-3.5 h-3.5" />
                  <span>Trở về</span>
                </button>
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

            {/* Quick Demo Login Button */}
            <button
              type="button"
              onClick={handleDemoLogin}
              disabled={loading}
              className="w-full py-3 px-4 bg-gradient-to-r from-amber-50 to-orange-50 hover:from-amber-100 hover:to-orange-100 border border-amber-200/90 rounded-2xl text-xs font-bold text-amber-900 shadow-xs transition-all flex items-center justify-between group"
            >
              <div className="flex items-center gap-2.5">
                <div className="p-1.5 bg-amber-500/10 rounded-lg text-amber-600 group-hover:scale-110 transition-transform">
                  <Sparkles className="w-4 h-4" />
                </div>
                <div className="text-left">
                  <p className="font-extrabold text-amber-950">Đăng nhập nhanh tài khoản mẫu</p>
                  <p className="text-[10px] text-amber-700/80 font-normal">Trải nghiệm ngay 1-Click không cần tạo mật khẩu</p>
                </div>
              </div>
              <ArrowRight className="w-4 h-4 text-amber-700 group-hover:translate-x-1 transition-transform" />
            </button>

            {/* Divider */}
            <div className="flex items-center gap-3 text-xs text-slate-400">
              <div className="flex-1 h-px bg-slate-200" />
              <span className="font-medium text-[11px]">HOẶC DÙNG EMAIL TÀI KHOẢN</span>
              <div className="flex-1 h-px bg-slate-200" />
            </div>

            {/* Error Message Alert */}
            {errorMessage && (
              <div className="bg-rose-50 border border-rose-200 text-rose-700 p-3.5 rounded-2xl text-xs flex items-center gap-2.5 animate-in fade-in">
                <AlertCircle className="w-4 h-4 shrink-0 text-rose-600" />
                <span className="font-medium">{errorMessage}</span>
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
                Bằng cách đăng nhập, bạn đồng ý với Điều khoản dịch vụ & Chính sách bảo mật của StudyMind AI.
              </p>
            </div>

          </div>

        </div>

      </div>
    </div>
  );
}
