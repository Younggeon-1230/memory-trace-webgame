import { Link, NavLink, Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { USE_MOCK } from '../api'
import { useAuth } from '../auth/AuthContext'

export function Brand() {
  return (
    <Link className="brand" to="/" aria-label="Memory Trace 홈">
      <span className="brand-mark" aria-hidden="true">M</span>
      <span><strong>MEMORY TRACE</strong><small>LAB INCIDENT ARCHIVE</small></span>
    </Link>
  )
}

export function TopBar() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const onLogout = async () => {
    await logout()
    navigate('/')
  }
  return (
    <header className="topbar">
      <Brand />
      <nav className="topnav" aria-label="주요 메뉴">
        {user ? (
          <>
            <NavLink to="/cases">사건 목록</NavLink>
            <NavLink to="/mypage">마이페이지</NavLink>
            <span className="topnav-user">{user.nickname} 님</span>
            <button type="button" className="ghost-button" onClick={onLogout}>로그아웃</button>
          </>
        ) : (
          <>
            <NavLink to="/signup">회원가입</NavLink>
            <Link className="ghost-button" to="/login">로그인</Link>
          </>
        )}
      </nav>
    </header>
  )
}

/** 상단 바 + 본문 + 하단 바. 게임 화면은 전체 화면을 쓰므로 이 레이아웃을 쓰지 않는다. */
export function SiteLayout() {
  return (
    <div className="app-shell">
      <TopBar />
      <Outlet />
      <footer>
        <span>MEMORY TRACE · WEB MYSTERY GAME</span>
        <span>{USE_MOCK ? 'MOCK SERVER MODE' : 'React + Spring Boot'}</span>
      </footer>
    </div>
  )
}

export function RequireAuth() {
  const { user, ready } = useAuth()
  const location = useLocation()
  if (!ready) return <div className="page-loading">수사 기록을 불러오는 중…</div>
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return <Outlet />
}

export function GuestOnly() {
  const { user, ready } = useAuth()
  if (!ready) return <div className="page-loading">수사 기록을 불러오는 중…</div>
  if (user) return <Navigate to="/cases" replace />
  return <Outlet />
}
