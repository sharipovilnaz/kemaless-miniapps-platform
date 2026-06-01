import type { DashboardMetricPoint } from '@/entities/dashboard'

import { ChartCard } from './ui/ChartCard'

import styles from './CallsCountRing.module.css'

const numberFormatter = new Intl.NumberFormat('ru-RU')

const sumCalls = (point: DashboardMetricPoint) =>
	point.launchSuccessTotal +
	point.launchDeniedTotal +
	point.ssoSuccessTotal +
	point.ssoFailedTotal

interface CallsCountRingProps {
	latest: DashboardMetricPoint
	periodLabel: string
}

const R = 88

export const CallsCountRing = ({ latest, periodLabel }: CallsCountRingProps) => {
	const total = sumCalls(latest)

	return (
		<ChartCard title={`Вызовы за период (${periodLabel})`}>
			<div className={styles.root}>
				<div className={styles.figure}>
					<svg aria-hidden className={styles.svg} viewBox='0 0 200 200'>
						<circle className={styles.ring} cx='100' cy='100' r={R} />
					</svg>
					<div className={styles.inner}>
						<p className={styles.value}>{numberFormatter.format(total)}</p>
						<p className={styles.caption}>вызовов</p>
					</div>
				</div>
			</div>
		</ChartCard>
	)
}
