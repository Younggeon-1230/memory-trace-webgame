import type { Suspect } from '../api'
import { COLOR_KO, ko, SHAPE_KO, STATUS_KO, TYPE_KO } from '../lib/labels'
import Modal from './Modal'

export default function SuspectFile({ suspect: s, basisName, onClose }: { suspect: Suspect; basisName?: string; onClose: () => void }) {
  return (
    <Modal title={`용의자 파일 · ${s.nameKo}`} eyebrow="SUSPECT FILE" onClose={onClose} wide>
      <div className="file-grid">
        <div className="file-photo">
          <img src={s.spritePath} alt={s.nameKo} width={160} height={160} />
          <span className={`pill ${s.status === 'ACTIVE' ? 'is-solved' : 'is-out-pill'}`}>{STATUS_KO[s.status]}</span>
          {basisName && <span className="muted small">근거: {basisName}</span>}
        </div>
        <dl className="file-facts">
          <div><dt>키</dt><dd>{s.heightM.toFixed(1)} m</dd></div>
          <div><dt>몸무게</dt><dd>{s.weightKg.toFixed(1)} kg</dd></div>
          <div><dt>타입</dt><dd>{s.types.map((t) => ko(TYPE_KO, t)).join(' · ')}</dd></div>
          <div><dt>대표 색</dt><dd>{ko(COLOR_KO, s.color)}</dd></div>
          <div><dt>체형</dt><dd>{ko(SHAPE_KO, s.shape)}</dd></div>
          <div><dt>의심 이유</dt><dd className="small-dd">{s.suspicionText}</dd></div>
        </dl>
      </div>
      <div className="file-traits">
        <strong>판정 관련 특징</strong>
        {s.traits.length ? (
          <ul>{s.traits.map((t) => <li key={t.traitCode}>{t.description}</li>)}</ul>
        ) : (
          <p className="muted small">이 사건 판정에 쓰이는 특별한 특징이 없습니다.</p>
        )}
      </div>
      <p className="muted small">데이터 출처: PokéAPI (도감 번호 {s.pokemonId}) · 키·몸무게는 원본 단위(dm·hg)를 m·kg로 바꿔 표시</p>
    </Modal>
  )
}
