import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'core/constants/app_constants.dart';
import 'core/utils/app_logger.dart';
import 'data/datasources/api_client.dart';
import 'data/datasources/auth_api.dart';
import 'data/datasources/socket_service.dart';
import 'data/datasources/token_store.dart';
import 'data/repositories/auth_repository_impl.dart';
import 'data/repositories/chat_repository_impl.dart';
import 'domain/repositories/auth_repository.dart';
import 'domain/repositories/chat_repository.dart';
import 'presentation/bloc/auth/auth_cubit.dart';
import 'presentation/screens/login_screen.dart';
import 'presentation/screens/rooms_screen.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final AppLogger log = AppLogger('main');
  try {
    final SharedPreferences prefs = await SharedPreferences.getInstance();
    final TokenStore tokenStore = TokenStore(prefs);
    final ApiClient apiClient = ApiClient(baseUrl: AppConstants.apiBaseUrl, tokenStore: tokenStore);
    final AuthApi authApi = AuthApi(apiClient);
    final SocketService socketService = SocketService();

    final AuthRepository authRepository = AuthRepositoryImpl(
      authApi: authApi,
      tokenStore: tokenStore,
      socketService: socketService,
      baseUrl: AppConstants.apiBaseUrl,
    );
    final ChatRepository chatRepository = ChatRepositoryImpl(
      socketService: socketService,
      apiClient: apiClient,
      tokenStore: tokenStore,
    );

    runApp(YugramApp(
      authRepository: authRepository,
      chatRepository: chatRepository,
    ));
  } catch (err, stackTrace) {
    log.error('Permulaan aplikasi gagal', err, stackTrace);
    runApp(_StartupErrorApp(details: '$err'));
  }
}

class YugramApp extends StatelessWidget {
  const YugramApp({
    required this.authRepository,
    required this.chatRepository,
    super.key,
  });

  final AuthRepository authRepository;
  final ChatRepository chatRepository;

  ThemeData _buildTheme(BuildContext context) {
    final ColorScheme scheme = ColorScheme.fromSeed(
      seedColor: const Color(0xFF7B5FE8),
      secondary: const Color(0xFF3FB6F5),
    );
    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      inputDecorationTheme: InputDecorationTheme(
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(14)),
        filled: true,
      ),
      appBarTheme: const AppBarTheme(centerTitle: false),
    );
  }

  @override
  Widget build(BuildContext context) {
    return MultiRepositoryProvider(
      providers: <RepositoryProvider<dynamic>>[
        RepositoryProvider<AuthRepository>.value(value: authRepository),
        RepositoryProvider<ChatRepository>.value(value: chatRepository),
      ],
      child: BlocProvider<AuthCubit>(
        create: (BuildContext context) {
          final AuthCubit cubit =
              AuthCubit(authRepository: context.read<AuthRepository>());
          cubit.restoreSession();
          return cubit;
        },
        child: MaterialApp(
          title: 'Yugram',
          debugShowCheckedModeBanner: false,
          theme: _buildTheme(context),
          home: const _AuthGate(),
        ),
      ),
    );
  }
}

/// Penghalal laluan mengikut keadaan AuthCubit.
class _AuthGate extends StatelessWidget {
  const _AuthGate();

  @override
  Widget build(BuildContext context) {
    return BlocBuilder<AuthCubit, AuthState>(
      builder: (BuildContext context, AuthState state) {
        if (state is AuthSuccess) {
          return RoomsScreen(session: state.session);
        }
        if (state is AuthUnauthenticated || state is AuthFailure) {
          return const LoginScreen();
        }
        // AuthInitial / AuthLoadInProgress - skrin splas ringkas.
        return const Scaffold(
          body: Center(child: CircularProgressIndicator()),
        );
      },
    );
  }
}

/// Skrin ralat permulaan dengan butang cuba semula.
class _StartupErrorApp extends StatelessWidget {
  const _StartupErrorApp({required this.details});

  final String details;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Yugram',
      debugShowCheckedModeBanner: false,
      home: Scaffold(
        body: SafeArea(
          child: Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: <Widget>[
                  Icon(Icons.error_outline,
                      size: 56, color: Theme.of(context).colorScheme.error),
                  const SizedBox(height: 16),
                  const Text(
                    'Aplikasi gagal dimulakan',
                    style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    details,
                    textAlign: TextAlign.center,
                    style: const TextStyle(fontSize: 12, color: Colors.grey),
                  ),
                  const SizedBox(height: 20),
                  FilledButton(
                    onPressed: () => main(),
                    child: const Text('Cuba Semula'),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
