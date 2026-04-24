import 'default_api_host_stub.dart'
    if (dart.library.io) 'default_api_host_io.dart' as impl;

String getPlatformDefaultGatewayBaseUrl() => impl.getPlatformDefaultGatewayBaseUrl();
