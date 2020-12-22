package no.nav.soknad.arkivering.soknadsarkiverer.supervision

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import io.prometheus.client.Summary
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
@Component
class ArchivingMetrics(private val registry: CollectorRegistry) {

	private val SOKNAD_NAMESPACE = "soknadinnsending"
	private val APP_LABEL = "app"
	private val APP = "soknadsarkiverer"


	private val GAUGE_TASKS = "gauge_tasks"
	private val GAUGE_TASKS_DESC = "Number of tasks in progress"

	private val GAUGE_TASKS_GIVEN_UP_ON = "gauge_tasks_given_up_on"
	private val GAUGE_TASKS_GIVEN_UP_ON_DESC = "Number of tasks given up on"

	private val SUMMARY_ARCHIVING_LATENCY = "latency_archiving_operations"
	private val SUMMARY_ARCHIVING_LATENCY_DESC = "Latency for archiving"

	private val COUNTER_FILESTORAGE_GET_SUCCESS = "counter_filestorage_get_success"
	private val COUNTER_FILESTORAGE_GET_SUCCESS_DESC = "Number of successful file retrievals from filestorage"

	private val COUNTER_FILESTORAGE_GET_ERROR = "counter_filestorage_get_error"
	private val COUNTER_FILESTORAGE_GET_ERROR_DESC = "Number of failing file retrievals from filestorage"

	private val COUNTER_FILESTORAGE_DEL_SUCCESS = "counter_filestorage_del_success"
	private val COUNTER_FILESTORAGE_DEL_SUCCESS_DESC = "Number of successful file deletions from filestorage"

	private val COUNTER_FILESTORAGE_DEL_ERROR = "counter_filestorage_del_error"
	private val COUNTER_FILESTORAGE_DEL_ERROR_DESC = "Number of failing file deletions from filestorage"

	private val SUMMARY_FILESTORAGE_GET_LATENCY = "latency_filestorage_get_operations"
	private val SUMMARY_FILESTORAGE_GET_LATENCY_DESC = "Latency for retrieving from filestorage"

	private val SUMMARY_FILESTORAGE_DEL_LATENCY = "latency_filestorage_del_operations"
	private val SUMMARY_FILESTORAGE_DEL_LATENCY_DESC = "Latency for deleting from filestorage"

	private val COUNTER_JOARK_SUCCESS = "counter_joark_success"
	private val COUNTER_JOARK_SUCCESS_DESC = "Number of successes when sending to Joark"

	private val COUNTER_JOARK_ERROR = "counter_joark_error"
	private val COUNTER_JOARK_ERROR_DESC = "Number of errors when sending to Joark"

	private val SUMMARY_JOARK_LATENCY = "latency_joark_operations"
	private val SUMMARY_JOARK_LATENCY_DESC = "Latency for sending to Joark"

	private val taskGauge: Gauge = registerGauge(GAUGE_TASKS, GAUGE_TASKS_DESC)
	private val tasksGivenUpOnGauge: Gauge = registerGauge(GAUGE_TASKS_GIVEN_UP_ON, GAUGE_TASKS_GIVEN_UP_ON_DESC)
	private val archivingLatencySummary = registerSummary(SUMMARY_ARCHIVING_LATENCY, SUMMARY_ARCHIVING_LATENCY_DESC)
	private val filestorageGetSuccessCounter: Counter = registerCounter(COUNTER_FILESTORAGE_GET_SUCCESS, COUNTER_FILESTORAGE_GET_SUCCESS_DESC)
	private val filestorageGetErrorCounter: Counter = registerCounter(COUNTER_FILESTORAGE_GET_ERROR, COUNTER_FILESTORAGE_GET_ERROR_DESC)
	private val filestorageDelSuccessCounter: Counter = registerCounter(COUNTER_FILESTORAGE_DEL_SUCCESS, COUNTER_FILESTORAGE_DEL_SUCCESS_DESC)
	private val filestorageDelErrorCounter: Counter = registerCounter(COUNTER_FILESTORAGE_DEL_ERROR, COUNTER_FILESTORAGE_DEL_ERROR_DESC)
	private val filestorageGetLatencySummary = registerSummary(SUMMARY_FILESTORAGE_GET_LATENCY, SUMMARY_FILESTORAGE_GET_LATENCY_DESC)
	private val filestorageDelLatencySummary = registerSummary(SUMMARY_FILESTORAGE_DEL_LATENCY, SUMMARY_FILESTORAGE_DEL_LATENCY_DESC)
	private val joarkSuccessCounter: Counter = registerCounter(COUNTER_JOARK_SUCCESS, COUNTER_JOARK_SUCCESS_DESC)
	private val joarkErrorCounter: Counter = registerCounter(COUNTER_JOARK_ERROR, COUNTER_JOARK_ERROR_DESC)
	private val joarkLatencySummary = registerSummary(SUMMARY_JOARK_LATENCY, SUMMARY_JOARK_LATENCY_DESC)

	private fun registerCounter(name: String, help: String): Counter =
		Counter
			.build()
			.namespace(SOKNAD_NAMESPACE)
			.name(name)
			.help(help)
			.labelNames(APP_LABEL)
			.register(registry)

	private fun registerGauge(name: String, help: String): Gauge =
		Gauge
			.build()
			.namespace(SOKNAD_NAMESPACE)
			.name(name)
			.help(help)
			.labelNames(APP_LABEL)
			.register(registry)

	private fun registerSummary(name: String, help: String): Summary =
		Summary
			.build()
			.namespace(SOKNAD_NAMESPACE)
			.name(name)
			.help(help)
			.quantile(0.5, 0.05)
			.quantile(0.9, 0.01)
			.quantile(0.99, 0.001)
			.labelNames(APP_LABEL)
			.register(registry)


	fun incGetFilestorageSuccesses() = filestorageGetSuccessCounter.labels(APP).inc()
	fun getGetFilestorageSuccesses() = filestorageGetSuccessCounter.labels(APP).get()

	fun incGetFilestorageErrors() = filestorageGetErrorCounter.labels(APP).inc()
	fun getGetFilestorageErrors() = filestorageGetErrorCounter.labels(APP).get()

	fun incDelFilestorageSuccesses() = filestorageDelSuccessCounter.labels(APP).inc()
	fun getDelFilestorageSuccesses() = filestorageDelSuccessCounter.labels(APP).get()

	fun incDelFilestorageErrors() = filestorageDelErrorCounter.labels(APP).inc()
	fun getDelFilestorageErrors() = filestorageDelErrorCounter.labels(APP).get()

	fun incJoarkSuccesses() = joarkSuccessCounter.labels(APP).inc()
	fun getJoarkSuccesses() = joarkSuccessCounter.labels(APP).get()

	fun incJoarkErrors() = joarkErrorCounter.labels(APP).inc()
	fun getJoarkErrors() = joarkErrorCounter.labels(APP).get()

	fun addTask() = taskGauge.labels(APP).inc()
	fun removeTask() = taskGauge.labels(APP).dec()
	fun getTasks() = taskGauge.labels(APP).get()

	fun setTasksGivenUpOn(value: Double) = tasksGivenUpOnGauge.labels(APP).set(value)
	fun getTasksGivenUpOn() = tasksGivenUpOnGauge.labels(APP).get()

	fun archivingLatencyStart(): Summary.Timer = archivingLatencySummary.labels(APP).startTimer()
	fun filestorageGetLatencyStart(): Summary.Timer = filestorageGetLatencySummary.labels(APP).startTimer()
	fun filestorageDelLatencyStart(): Summary.Timer = filestorageDelLatencySummary.labels(APP).startTimer()
	fun joarkLatencyStart(): Summary.Timer = joarkLatencySummary.labels(APP).startTimer()

	fun endTimer(timer: Summary.Timer) {
		timer.observeDuration()
	}

	fun unregister() {
		registry.unregister(joarkErrorCounter)
		registry.unregister(joarkSuccessCounter)
		registry.unregister(filestorageDelErrorCounter)
		registry.unregister(filestorageDelSuccessCounter)
		registry.unregister(filestorageGetErrorCounter)
		registry.unregister(filestorageGetSuccessCounter)
		registry.unregister(joarkLatencySummary)
		registry.unregister(filestorageDelLatencySummary)
		registry.unregister(filestorageGetLatencySummary)
		registry.unregister(archivingLatencySummary)
		registry.unregister(tasksGivenUpOnGauge)
		registry.unregister(taskGauge)
	}

}
