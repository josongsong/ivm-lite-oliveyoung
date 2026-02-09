import { Database } from 'lucide-react'

interface RawDataErrorStateProps {
  error: Error
  entityId: string
  tenant: string
}

export function RawDataErrorState({ error, entityId, tenant }: RawDataErrorStateProps) {
  let errorMessage = '데이터를 불러오는 중 오류가 발생했습니다.'
  let errorDetails: string | null = null

  if ('status' in error && 'serverMessage' in error) {
    const apiError = error as Error & { status: number; serverMessage?: string }
    const status = apiError.status
    const serverMessage = apiError.serverMessage

    if (status === 404 || serverMessage?.includes('not found') || serverMessage?.includes('NOT_FOUND')) {
      errorMessage = 'RawData를 찾을 수 없습니다'
      errorDetails = `Entity ID: ${entityId}\nTenant: ${tenant}`
    } else {
      errorMessage = serverMessage || error.message || errorMessage
      errorDetails = `상태 코드: ${status}`
    }
  } else {
    errorMessage = error.message || errorMessage
  }

  return (
    <div className="rawdata-panel error">
      <Database size={48} />
      <div className="error-content">
        <h3>{errorMessage}</h3>
        {errorDetails ? (
          <div className="error-details">
            <pre>{errorDetails}</pre>
          </div>
        ) : null}
        <div className="error-info">
          <p>데이터베이스: DynamoDB (AWS)</p>
          <p>테이블: ivm-lite-data</p>
          <p>데이터가 없거나 조회 권한이 없을 수 있습니다.</p>
        </div>
      </div>
    </div>
  )
}
