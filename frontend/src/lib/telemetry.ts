/**
 * OpenTelemetry Web Instrumentation
 * Frontend Observability for Elastic Stack Integration
 *
 * Features:
 * - Automatic fetch/XHR request tracing
 * - Document load performance metrics
 * - Trace context propagation to backend services
 *
 * Author: Hassan Rawashdeh
 * Date: 2026-01-01
 */

import { WebTracerProvider } from '@opentelemetry/sdk-trace-web';
import { SimpleSpanProcessor } from '@opentelemetry/sdk-trace-web';
import { ConsoleSpanExporter } from '@opentelemetry/sdk-trace-web';
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http';
import { registerInstrumentations } from '@opentelemetry/instrumentation';
import { DocumentLoadInstrumentation } from '@opentelemetry/instrumentation-document-load';
import { FetchInstrumentation } from '@opentelemetry/instrumentation-fetch';
import { XMLHttpRequestInstrumentation } from '@opentelemetry/instrumentation-xml-http-request';
import { ZoneContextManager } from '@opentelemetry/context-zone';

// Detect environment
const isProduction = import.meta.env.PROD || import.meta.env.MODE === 'production';
const environment = isProduction ? 'production' : 'development';
const appVersion = '1.2.0';

// OTel Collector endpoint (OTLP HTTP)
const collectorEndpoint = isProduction
  ? '/otlp/v1/traces'
  : 'http://localhost:30080/otlp/v1/traces';  // Via frontend NGINX proxy

let tracer: any = null;
let telemetryInitialized = false;

// Initialize OpenTelemetry in a non-blocking way
// If it fails, the app should still work, just without tracing
try {
  console.log(`[OpenTelemetry] Initializing frontend tracing...`);
  console.log(`[OpenTelemetry] Environment: ${environment}`);
  console.log(`[OpenTelemetry] Service: loader-frontend v${appVersion}`);
  console.log(`[OpenTelemetry] Collector: ${collectorEndpoint}`);

  // Create OTLP exporter (HTTP)
  const exporter = new OTLPTraceExporter({
    url: collectorEndpoint,
    headers: {
      'Content-Type': 'application/json',
    },
  });

  // Create tracer provider with span processor
  const provider = new WebTracerProvider();

  // Configure span processors (must be done before register)
  const processors = [new SimpleSpanProcessor(exporter)];
  if (!isProduction) {
    processors.push(new SimpleSpanProcessor(new ConsoleSpanExporter()));
  }

  // Add processors using internal method
  processors.forEach(processor => {
    (provider as any).addSpanProcessor(processor);
  });

  // Register the provider
  provider.register({
    contextManager: new ZoneContextManager(),
  });

  // Register instrumentations
  registerInstrumentations({
    instrumentations: [
      new DocumentLoadInstrumentation(),
      new FetchInstrumentation({
        propagateTraceHeaderCorsUrls: /.+/,
        clearTimingResources: true,
      }),
      new XMLHttpRequestInstrumentation({
        propagateTraceHeaderCorsUrls: /.+/,
        clearTimingResources: true,
      }),
    ],
  });

  tracer = provider.getTracer('loader-frontend', appVersion);
  telemetryInitialized = true;

  console.log('[OpenTelemetry] Frontend tracing initialized successfully');
} catch (error) {
  console.warn('[OpenTelemetry] Failed to initialize frontend tracing:', error);
  console.warn('[OpenTelemetry] App will continue without telemetry');
  // Don't throw - let the app continue without telemetry
}

export { tracer, telemetryInitialized };
