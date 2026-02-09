export function getMissingPolicyLabel(policy: string): string {
  const labels: Record<string, string> = {
    'FAIL_CLOSED': '오류 발생 (Fail Closed)',
    'PARTIAL_ALLOWED': '부분 결과 허용',
    'NONE': '정책 없음'
  }
  return labels[policy] || policy
}
