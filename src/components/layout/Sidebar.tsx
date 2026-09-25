import React from 'react';
import { 
  LayoutDashboard, 
  UploadCloud, 
  BookOpen, 
  GitFork, 
  Layers, 
  HelpCircle,
  BrainCircuit,
  Sparkles,
  History,
  GitCompare,
  LogIn,
  LogOut,
  Compass
} from 'lucide-react';
import { useStudy } from '../../context/StudyContext';
import { TabType } from '../../types';

export function Sidebar() {
  const { activeTab, setActiveTab, user, isAuthenticated, openAuthModal, openEditProfileModal, logout } = useStudy();

  const navItems: Array<{ id: TabType; label: string; icon: any }> = [
    { id: 'dashboard', label: 'Tổng quan', icon: LayoutDashboard },
    { id: 'import', label: 'Nhập tài liệu', icon: UploadCloud },
    { id: 'history', label: 'Lịch sử bài học', icon: History },
    { id: 'workspace', label: 'Không gian tài liệu', icon: BookOpen },
    { id: 'prerequisite', label: 'Kiến thức tiên quyết', icon: Compass },
    { id: 'mindmap', label: 'Sơ đồ tư duy', icon: GitFork },
    { id: 'flashcard', label: 'Thẻ ghi nhớ', icon: Layers },
    { id: 'quiz', label: 'Trắc nghiệm', icon: HelpCircle },
  ];

  return (
    <aside className="w-64 bg-white border-r border-slate-200 flex flex-col justify-between h-screen sticky top-0 z-40 hidden md:flex">
      {/* Brand Header */}
      <div>
        <div className="p-6 flex items-center gap-3 border-b border-slate-100">
          <div className="w-9 h-9 rounded-xl bg-[#0F766E] flex items-center justify-center text-white shadow-md shadow-[#0F766E]/20">
            <BrainCircuit className="w-5 h-5" />
          </div>
          <div>
            <h1 className="font-bold text-lg text-[#111827] tracking-tight leading-none">
              StudyMind <span className="text-[#0F766E]">AI</span>
            </h1>
            <p className="text-[11px] text-slate-500 font-medium mt-0.5">
              Trợ lý học tập thông minh
            </p>
          </div>
        </div>

        {/* Navigation Menu */}
        <nav className="p-4 space-y-1.5">
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive = activeTab === item.id;
            return (
              <button
                key={item.id}
                onClick={() => setActiveTab(item.id)}
                className={`w-full flex items-center gap-3 px-3.5 py-2.5 rounded-lg text-sm font-medium transition-all ${
                  isActive
                    ? 'bg-[#CCFBF1] text-[#0F766E] font-semibold shadow-xs'
                    : 'text-[#4B5563] hover:bg-slate-50 hover:text-[#111827]'
                }`}
              >
                <Icon className={`w-4 h-4 ${isActive ? 'text-[#0F766E]' : 'text-slate-400'}`} />
                <span>{item.label}</span>
              </button>
            );
          })}
        </nav>
      </div>

      {/* User Bottom Widget */}
      <div className="p-4 border-t border-slate-100">
        {isAuthenticated ? (
          <div className="bg-slate-50 hover:bg-slate-100/80 transition-colors p-2.5 rounded-xl flex items-center justify-between border border-slate-200/60">
            <button
              onClick={openEditProfileModal}
              className="flex items-center gap-2.5 overflow-hidden text-left group flex-1"
              title="Chỉnh sửa hồ sơ"
            >
              <img
                src={user.avatarUrl}
                alt={user.fullName}
                className="w-9 h-9 rounded-full object-cover ring-2 ring-emerald-500/20 shrink-0 group-hover:scale-105 transition-transform"
                onError={(e) => {
                  (e.target as HTMLImageElement).src = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150&auto=format&fit=crop&q=80";
                }}
              />
              <div className="overflow-hidden">
                <p className="text-xs font-bold text-[#111827] group-hover:text-[#0F766E] transition-colors truncate">
                  {user.fullName}
                </p>
                <div className="flex items-center gap-1 text-[10px] text-[#0F766E] font-medium mt-0.5">
                  <Sparkles className="w-2.5 h-2.5" />
                  <span>{user.membershipTier || 'Premium'}</span>
                </div>
              </div>
            </button>

            <button
              onClick={logout}
              className="p-1.5 text-slate-400 hover:text-rose-600 rounded-lg hover:bg-rose-50 transition-colors shrink-0"
              title="Đăng xuất"
            >
              <LogOut className="w-4 h-4" />
            </button>
          </div>
        ) : (
          <button
            onClick={() => openAuthModal('login')}
            className="w-full bg-[#0F766E] hover:bg-[#0D645E] text-white py-2.5 px-3 rounded-xl text-xs font-bold transition-all flex items-center justify-center gap-2 shadow-xs"
          >
            <LogIn className="w-4 h-4" />
            <span>Đăng nhập tài khoản</span>
          </button>
        )}
      </div>
    </aside>
  );
}
