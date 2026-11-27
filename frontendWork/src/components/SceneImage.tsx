import { useState } from 'react'

/** 현장 이미지. 아직 이미지가 없는 사건(CASE 01·02)은 자리표시 화면을 보여 준다. */
export default function SceneImage({ src, alt, className }: { src: string; alt: string; className?: string }) {
  const [failed, setFailed] = useState(false)
  if (failed) {
    return (
      <div className={`scene-placeholder ${className ?? ''}`} role="img" aria-label={`${alt} (이미지 준비 중)`}>
        <span>SCENE IMAGE</span>
        <strong>현장 이미지 준비 중</strong>
      </div>
    )
  }
  return <img className={className} src={src} alt={alt} onError={() => setFailed(true)} draggable={false} />
}
