package utils.logger

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.{Level, LoggerContext}
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.{Appender, ConsoleAppender, FileAppender}
import config.GlobalConfig

import java.util.logging.{LogManager, Logger => JulLogger}
import org.slf4j.bridge.SLF4JBridgeHandler

object LoggerFactoryUtil {

  private var isGrpcLoggingConfigured = false // gRPC 로그 설정 상태

  // gRPC 로그 설정
  private def configureGrpcLogging(): Unit = {
    if (!isGrpcLoggingConfigured) {
      LogManager.getLogManager.reset()
      SLF4JBridgeHandler.install()

      val grpcLogger = JulLogger.getLogger("io.grpc")
      if (GlobalConfig.isDebugging) {
        grpcLogger.setLevel(java.util.logging.Level.FINE) // DEBUG 수준
      } else {
        grpcLogger.setLevel(java.util.logging.Level.INFO) // INFO 수준
      }

      isGrpcLoggingConfigured = true // 한 번만 설정되도록 플래그 업데이트
    }
  }

  // Root Logger 초기화
  private def resetRootLogger(context: LoggerContext): Unit = {
    val rootLogger = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
    rootLogger.detachAndStopAllAppenders() // 기존 Appender 제거
  }

  // 일반 로그 파일 Appender 생성
  private def createFileAppender(
      context: LoggerContext
  ): Appender[ILoggingEvent] = {
    val fileAppender = new FileAppender[ILoggingEvent]()
    fileAppender.setName("FILE")
    fileAppender.setContext(context) // Context 설정
    val encoder = new ch.qos.logback.classic.encoder.PatternLayoutEncoder()
    encoder.setContext(context) // Encoder에 Context 설정
    encoder.setPattern(
      "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
    )
    encoder.start() // Context 설정 후 start 호출
    fileAppender.setEncoder(encoder) // Encoder 연결
    fileAppender.setFile("logs/application.log")
    fileAppender.setAppend(true)
    fileAppender.start() // Appender 시작
    fileAppender
  }

  // gRPC 전용 로그 파일 Appender 생성
  private def createGrpcFileAppender(
      context: LoggerContext
  ): Appender[ILoggingEvent] = {
    val grpcFileAppender = new FileAppender[ILoggingEvent]()
    grpcFileAppender.setName("GRPC_FILE")
    grpcFileAppender.setContext(context) // Context 설정
    val encoder = new ch.qos.logback.classic.encoder.PatternLayoutEncoder()
    encoder.setContext(context) // Encoder에 Context 설정
    encoder.setPattern(
      "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
    )
    encoder.start() // Context 설정 후 start 호출
    grpcFileAppender.setEncoder(encoder) // Encoder 연결
    grpcFileAppender.setFile("logs/grpc.log") // gRPC 로그 파일
    grpcFileAppender.setAppend(true)
    grpcFileAppender.start() // Appender 시작
    grpcFileAppender
  }

  // 콘솔 Appender 생성
  private def createConsoleAppender(
      context: LoggerContext,
      level: Level
  ): Appender[ILoggingEvent] = {
    val consoleAppender = new ConsoleAppender[ILoggingEvent]()
    consoleAppender.setName("STDOUT")
    consoleAppender.setContext(context) // Context 설정
    val encoder = new ch.qos.logback.classic.encoder.PatternLayoutEncoder()
    encoder.setContext(context) // Encoder에 Context 설정
    encoder.setPattern(
      "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
    )
    encoder.start() // Context 설정 후 start 호출
    consoleAppender.setEncoder(encoder) // Encoder 연결
    consoleAppender.addFilter(new ch.qos.logback.classic.filter.LevelFilter {
      setLevel(level)
      setOnMatch(ch.qos.logback.core.spi.FilterReply.ACCEPT)
      setOnMismatch(ch.qos.logback.core.spi.FilterReply.DENY)
    })
    consoleAppender.start() // Appender 시작
    consoleAppender
  }

  // Logger 생성
  def getLogger(name: String): Logger = {
    configureGrpcLogging() // gRPC 로그 설정

    val context = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    resetRootLogger(context) // 기존 설정 초기화

    // 일반 애플리케이션 로거 설정
    val rootLogger = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
    rootLogger.addAppender(createFileAppender(context)) // 일반 로그 파일
    rootLogger.addAppender(
      createConsoleAppender(context, Level.INFO)
    ) // 터미널 출력은 INFO로 제한
    rootLogger.setLevel(
      if (GlobalConfig.isDebugging) Level.DEBUG else Level.INFO
    )

    // gRPC 및 Netty 전용 로거 설정
    val grpcLogger = context.getLogger("io.grpc")
    grpcLogger.addAppender(createGrpcFileAppender(context)) // gRPC 로그 파일
    grpcLogger.setAdditive(false) // 루트 로거로 전달 차단
    grpcLogger.setLevel(
      if (GlobalConfig.isDebugging) Level.DEBUG else Level.INFO
    )

    val nettyLogger = context.getLogger("io.netty")
    nettyLogger.addAppender(
      createGrpcFileAppender(context)
    ) // Netty 로그도 gRPC 파일에 기록
    nettyLogger.setAdditive(false) // 루트 로거로 전달 차단
    nettyLogger.setLevel(
      if (GlobalConfig.isDebugging) Level.DEBUG else Level.INFO
    )

    Logger(name)
  }
}
