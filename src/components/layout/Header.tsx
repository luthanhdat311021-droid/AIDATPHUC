import React, { useState } from 'react';
import { Search, Bell, Sparkles, LogIn, LogOut, User as UserIcon, ChevronDown } from 'lucide-react';
import { useStudy } from '../../context/StudyContext';

export function Header() {
  const { user, isAuthenticated, openAuthModal, openEditProfileModal, logout } = useStudy();
  const [showDropdown, setShowDropdown] = useState(false);

  return (
    <header className="h-16 bg-white border-b border-slate-200 px-4 md:px-8 flex items-center justify-between sticky top-0 z-30 shadow-xs">
      {/* Search Bar */}
      <div className="flex-1 max-w-xl relative">
        <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
        <input
          type="text"
          placeholder="Tìm kiếm tài liệu, sơ đồ..."
          className="w-full bg-slate-50 hover:bg-slate-100 focus:bg-white transition-all text-xs md:text-sm pl-9 pr-4 py-2 rounded-lg border border-slate-200 focus:outline-none focus:border-[#0F766E] focus:ring-1 focus:ring-[#0F766E] placeholder:text-slate-400"
        />
      </div>

      {/* Right User Controls */}
      <div className="flex items-center gap-3 md:gap-4 ml-4">
        {/* Notification Icon */}
        <button className="relative p-2 rounded-full hover:bg-slate-100 text-slate-600 transition-colors">
          <Bell className="w-5 h-5" />
          <span className="absolute top-1.5 right-1.5 w-2 h-2 bg-emerald-500 rounded-full ring-2 ring-white animate-pulse" />
        </button>

        {/* Auth / Profile Section */}
        {isAuthenticated ? (
          <div className="relative">
            <button
              onClick={() => setShowDropdown(!showDropdown)}
              className="flex items-center gap-2 pl-2 border-l border-slate-200 hover:opacity-90 transition-opacity"
            >
              <img
                src={user.avatarUrl}
                alt={user.fullName}
                className="w-8 h-8 md:w-9 md:h-9 rounded-full object-cover ring-2 ring-[#CCFBF1]"
                onError={(e) => {
                  (e.target as HTMLImageElement).src = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150&auto=format&fit=crop&q=80";
                }}
              />
              <div className="hidden sm:block text-left">
                <h4 className="text-xs md:text-sm font-semibold text-[#111827] leading-tight">
                  {user.fullName}
                </h4>
                <div className="flex items-center gap-1 text-[11px] text-[#0F766E] font-medium">
                  <Sparkles className="w-3 h-3 text-[#0F766E]" />
                  <span>{user.membershipTier || 'Premium'}</span>
                </div>
              </div>
              <ChevronDown className="w-4 h-4 text-slate-400 hidden sm:block" />
            </button>

            {/* Dropdown Menu */}
            {showDropdown && (
              <div className="absolute right-0 mt-2 w-52 bg-white rounded-2xl shadow-xl border border-slate-200/80 p-2 z-50 animate-in fade-in zoom-in-95">
                <div className="p-3 bg-slate-50 rounded-xl mb-1 border border-slate-100">
                  <p className="text-xs font-bold text-slate-900 truncate">{user.fullName}</p>
                  <p className="text-[11px] text-slate-500 truncate mt-0.5">{user.email || 'Hội viên VIP'}</p>
                </div>

                <button
                  onClick={() => { setShowDropdown(false); openEditProfileModal(); }}
                  className="w-full flex items-center gap-2.5 px-3 py-2 rounded-xl text-xs font-bold text-slate-700 hover:bg-slate-100 transition-colors mb-1"
                >
                  <UserIcon className="w-4 h-4 text-[#0F766E]" />
                  <span>Chỉnh sửa hồ sơ</span>
                </button>

                <button
                  onClick={() => { setShowDropdown(false); logout(); }}
                  className="w-full flex items-center gap-2.5 px-3 py-2 rounded-xl text-xs font-bold text-rose-600 hover:bg-rose-50 transition-colors"
                >
                  <LogOut className="w-4 h-4" />
                  <span>Đăng xuất tài khoản</span>
                </button>
              </div>
            )}
          </div>
        ) : (
          <div className="pl-2 border-l border-slate-200 flex items-center gap-2">
            <button
              onClick={() => openAuthModal('login')}
              className="bg-[#0F766E] hover:bg-[#0D645E] text-white text-xs font-bold px-4 py-2 rounded-xl shadow-xs transition-all flex items-center gap-2"
            >
              <LogIn className="w-4 h-4" />
              <span>Đăng nhập / Đăng ký</span>
            </button>
          </div>
        )}
      </div>
    </header>
  );
}
