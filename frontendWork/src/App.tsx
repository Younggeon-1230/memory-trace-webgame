import { Navigate, Route, Routes } from 'react-router-dom'
import { GuestOnly, RequireAuth, SiteLayout } from './components/Layout'
import CasesPage from './pages/CasesPage'
import GamePage from './pages/GamePage'
import LoginPage from './pages/LoginPage'
import MainPage from './pages/MainPage'
import MyPage from './pages/MyPage'
import ResultPage from './pages/ResultPage'
import SignupPage from './pages/SignupPage'

// 화면설계서: 메인 → 로그인/회원가입 → 사건 목록 → 게임(용의자 파일·지목) → 결과 → 마이페이지
export default function App() {
  return (
    <Routes>
      <Route element={<SiteLayout />}>
        <Route path="/" element={<MainPage />} />
        <Route element={<GuestOnly />}>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/signup" element={<SignupPage />} />
        </Route>
        <Route element={<RequireAuth />}>
          <Route path="/cases" element={<CasesPage />} />
          <Route path="/result/:sessionId" element={<ResultPage />} />
          <Route path="/mypage" element={<MyPage />} />
        </Route>
      </Route>
      <Route element={<RequireAuth />}>
        <Route path="/play/:sessionId" element={<GamePage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
