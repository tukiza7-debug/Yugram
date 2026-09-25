import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../bloc/auth/auth_cubit.dart';
import '../widgets/animated_yugram_logo.dart';

/// Skrin log masuk + pendaftaran (mod ditogol).
class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final GlobalKey<FormState> _formKey = GlobalKey<FormState>();
  final TextEditingController _usernameController = TextEditingController();
  final TextEditingController _displayNameController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();

  bool _isRegisterMode = false;

  @override
  void dispose() {
    _usernameController.dispose();
    _displayNameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  void _submit() {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    final AuthCubit cubit = context.read<AuthCubit>();
    if (_isRegisterMode) {
      cubit.register(
        username: _usernameController.text.trim(),
        displayName: _displayNameController.text.trim(),
        password: _passwordController.text,
      );
    } else {
      cubit.login(
        username: _usernameController.text.trim(),
        password: _passwordController.text,
      );
    }
  }

  String? _validateUsername(String? value) {
    final String text = (value ?? '').trim();
    if (text.length < 3) {
      return 'Sekurang-kurangnya 3 aksara';
    }
    if (!RegExp(r'^[a-zA-Z0-9_]+$').hasMatch(text)) {
      return 'Hanya huruf, nombor dan underscore';
    }
    return null;
  }

  String? _validatePassword(String? value) {
    final String text = value ?? '';
    if (text.length < 8) {
      return 'Sekurang-kurangnya 8 aksara';
    }
    if (_isRegisterMode && !RegExp(r'^(?=.*[A-Za-z])(?=.*\d)').hasMatch(text)) {
      return 'Mesti ada huruf dan nombor';
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    final ColorScheme scheme = Theme.of(context).colorScheme;
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: Form(
              key: _formKey,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: <Widget>[
                  const Center(
                    child: AnimatedYugramLogo(size: 104, showSparks: false),
                  ),
                  const SizedBox(height: 16),
                  Text(
                    'Yugram',
                    textAlign: TextAlign.center,
                    style: Theme.of(context)
                        .textTheme
                        .headlineMedium
                        ?.copyWith(fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    _isRegisterMode ? 'Cipta akaun baharu' : 'Selamat kembali',
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                          color: scheme.onSurfaceVariant,
                        ),
                  ),
                  const SizedBox(height: 28),
                  TextFormField(
                    controller: _usernameController,
                    decoration: const InputDecoration(
                      labelText: 'Username',
                      prefixIcon: Icon(Icons.alternate_email),
                    ),
                    textInputAction: TextInputAction.next,
                    autofillHints: const <String>[AutofillHints.username],
                    validator: _validateUsername,
                  ),
                  if (_isRegisterMode) ...<Widget>[
                    const SizedBox(height: 12),
                    TextFormField(
                      controller: _displayNameController,
                      decoration: const InputDecoration(
                        labelText: 'Nama Paparan',
                        prefixIcon: Icon(Icons.badge_outlined),
                      ),
                      textInputAction: TextInputAction.next,
                      autofillHints: const <String>[AutofillHints.name],
                      validator: (String? value) =>
                          (value ?? '').trim().isEmpty ? 'Masukkan nama paparan' : null,
                    ),
                  ],
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: _passwordController,
                    decoration: const InputDecoration(
                      labelText: 'Kata Laluan',
                      prefixIcon: Icon(Icons.lock_outline),
                    ),
                    obscureText: true,
                    autofillHints: const <String>[AutofillHints.password],
                    validator: _validatePassword,
                    onFieldSubmitted: (_) => _submit(),
                  ),
                  const SizedBox(height: 24),
                  BlocBuilder<AuthCubit, AuthState>(
                    builder: (BuildContext context, AuthState state) {
                      final bool loading = state is AuthLoadInProgress;
                      return FilledButton(
                        onPressed: loading ? null : _submit,
                        style: FilledButton.styleFrom(
                          padding: const EdgeInsets.symmetric(vertical: 14),
                          backgroundColor: scheme.primary,
                        ),
                        child: loading
                            ? const SizedBox(
                                width: 22,
                                height: 22,
                                child: CircularProgressIndicator(strokeWidth: 2),
                              )
                            : Text(_isRegisterMode ? 'Daftar' : 'Masuk'),
                      );
                    },
                  ),
                  const SizedBox(height: 8),
                  TextButton(
                    onPressed: () => setState(() => _isRegisterMode = !_isRegisterMode),
                    child: Text(_isRegisterMode
                        ? 'Sudah ada akaun? Masuk'
                        : 'Tiada akaun? Daftar sekarang'),
                  ),
                  BlocConsumer<AuthCubit, AuthState>(
                    listener: (BuildContext context, AuthState state) {
                      if (state is AuthFailure) {
                        ScaffoldMessenger.of(context)
                          ..hideCurrentSnackBar()
                          ..showSnackBar(
                            SnackBar(
                              content: Text(state.message),
                              backgroundColor: scheme.errorContainer,
                            ),
                          );
                      }
                    },
                    builder: (BuildContext context, AuthState state) => const SizedBox.shrink(),
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
