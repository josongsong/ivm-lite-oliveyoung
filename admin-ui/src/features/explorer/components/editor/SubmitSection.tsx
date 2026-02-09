import { AnimatePresence, motion } from 'framer-motion'
import { AlertCircle, Check, Send } from 'lucide-react'
import { Button } from '@/shared/ui'

interface SubmitSectionProps {
  isFormValid: boolean
  isPending: boolean
  isSuccess: boolean
  isError: boolean
  errorMessage?: string
  onSubmit: () => void
}

export function SubmitSection({
  isFormValid,
  isPending,
  isSuccess,
  isError,
  errorMessage,
  onSubmit,
}: SubmitSectionProps) {
  return (
    <>
      <div className="form-submit">
        <Button
          variant="primary"
          size="lg"
          onClick={onSubmit}
          disabled={!isFormValid || isPending}
          loading={isPending}
          icon={!isPending ? <Send size={16} /> : undefined}
          className="submit-btn"
        >
          {isPending ? '등록 중...' : 'RawData 등록'}
        </Button>
      </div>

      <AnimatePresence>
        {isSuccess ? (
          <motion.div
            className="submit-result success"
            initial={{ opacity: 0, y: -10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
          >
            <Check size={16} />
            <span>RawData가 성공적으로 등록되었습니다!</span>
          </motion.div>
        ) : null}
        {isError ? (
          <motion.div
            className="submit-result error"
            initial={{ opacity: 0, y: -10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
          >
            <AlertCircle size={16} />
            <span>등록 실패: {errorMessage}</span>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </>
  )
}
