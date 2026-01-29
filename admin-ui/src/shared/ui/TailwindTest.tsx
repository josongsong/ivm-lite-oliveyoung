// Tailwind CSS 테스트 컴포넌트
export function TailwindTest() {
  return (
    <div className="bg-bg-primary text-text-primary p-4 rounded-lg">
      <h1 className="text-accent-cyan text-2xl font-bold mb-4">
        Tailwind CSS 테스트
      </h1>
      <div className="bg-bg-card border border-border p-4 rounded-md">
        <p className="text-text-secondary">
          Tailwind가 정상 작동하면 이 박스가 보입니다.
        </p>
        <button className="mt-4 bg-accent-cyan text-bg-primary px-4 py-2 rounded-md hover:bg-accent-magenta transition-colors">
          테스트 버튼
        </button>
      </div>
    </div>
  )
}
