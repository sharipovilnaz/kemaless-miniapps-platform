export interface DashboardMetricPoint {
	timestamp: string
	launchSuccessTotal: number
	launchDeniedTotal: number
	ssoSuccessTotal: number
	ssoFailedTotal: number
}

export interface DashboardMetricChartPoint extends DashboardMetricPoint {
	timeLabel: string
	launchSuccessDelta: number
	launchDeniedDelta: number
	ssoSuccessDelta: number
	ssoFailedDelta: number
}

export interface RealmMiniAppMetrics {
	cachedAtEpochSecond: number
	currentStatus: string
	lastWindowSeconds: number
	launchesDenied: number
	launchesSuccess: number
	miniAppCode: string
	miniAppName: string
	realmCode: string
	ssoFailed: number
	ssoSuccess: number
	totalRequests: number
}
