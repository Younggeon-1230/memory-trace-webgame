import { useEffect, useId, type ReactNode } from 'react'

type Props = { title: string; eyebrow?: string; onClose: () => void; children: ReactNode; small?: boolean; wide?: boolean }

export default function Modal({ title, eyebrow, onClose, children, small, wide }: Props) {
  const titleId = useId()
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={onClose}>
      <section
        className={`modal ${small ? 'is-small' : ''} ${wide ? 'is-wide' : ''}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onMouseDown={(e) => e.stopPropagation()}
      >
        <button type="button" className="close-button" onClick={onClose} aria-label="닫기">×</button>
        {eyebrow && <p className="eyebrow"><span /> {eyebrow}</p>}
        <h2 id={titleId}>{title}</h2>
        {children}
      </section>
    </div>
  )
}
