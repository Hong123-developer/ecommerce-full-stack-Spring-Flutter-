import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:image_picker/image_picker.dart';

import '../../config/theme.dart';
import '../../providers/providers.dart';
import '../../widgets/auth_widgets.dart';

class PersonalDetailsScreen extends ConsumerStatefulWidget {
  const PersonalDetailsScreen({super.key});

  @override
  ConsumerState<PersonalDetailsScreen> createState() => _PersonalDetailsScreenState();
}

class _PersonalDetailsScreenState extends ConsumerState<PersonalDetailsScreen> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _nameCtrl;
  late final TextEditingController _phoneCtrl;
  bool _saving = false;
  AutovalidateMode _autovalidate = AutovalidateMode.disabled;

  // Newly picked photo (preview) and its URL once uploaded, so a retry after a
  // failed save doesn't upload the same image twice.
  Uint8List? _pickedBytes;
  String? _pickedName;
  String? _uploadedUrl;

  @override
  void initState() {
    super.initState();
    final user = ref.read(authProvider).user;
    _nameCtrl = TextEditingController(text: user?.fullName ?? '');
    _phoneCtrl = TextEditingController(text: user?.phone ?? '');
  }

  @override
  void dispose() {
    _nameCtrl.dispose();
    _phoneCtrl.dispose();
    super.dispose();
  }

  Future<void> _choosePhoto() async {
    // Camera capture isn't available on web
    final source = kIsWeb
        ? ImageSource.gallery
        : await showModalBottomSheet<ImageSource>(
            context: context,
            backgroundColor: AppColors.surface,
            shape: const RoundedRectangleBorder(
              borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
            ),
            builder: (sheetContext) => SafeArea(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  ListTile(
                    leading: const Icon(Icons.photo_camera_outlined),
                    title: const Text('Take a photo'),
                    onTap: () => Navigator.of(sheetContext).pop(ImageSource.camera),
                  ),
                  ListTile(
                    leading: const Icon(Icons.photo_library_outlined),
                    title: const Text('Choose from gallery'),
                    onTap: () => Navigator.of(sheetContext).pop(ImageSource.gallery),
                  ),
                ],
              ),
            ),
          );
    if (source == null) return;

    try {
      final file = await ImagePicker().pickImage(
        source: source,
        maxWidth: 800,
        maxHeight: 800,
        imageQuality: 85,
      );
      if (file == null) return;
      final bytes = await file.readAsBytes();
      if (!mounted) return;
      setState(() {
        _pickedBytes = bytes;
        _pickedName = file.name;
        _uploadedUrl = null;
      });
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Could not open photos: $e')),
        );
      }
    }
  }

  Future<void> _save() async {
    FocusScope.of(context).unfocus();
    if (!(_formKey.currentState?.validate() ?? false)) {
      setState(() => _autovalidate = AutovalidateMode.onUserInteraction);
      return;
    }
    final notifier = ref.read(authProvider.notifier);
    setState(() => _saving = true);

    // The backend clears the photo when profileImage is null, so keep the current one
    String? imageUrl = ref.read(authProvider).user?.profileImage;
    if (_pickedBytes != null) {
      _uploadedUrl ??= await notifier.uploadAvatar(_pickedBytes!, _pickedName ?? 'avatar.jpg');
      if (_uploadedUrl == null) {
        _showError('Could not upload photo');
        return;
      }
      imageUrl = _uploadedUrl;
    }

    final success = await notifier.updateProfile(
      fullName: _nameCtrl.text.trim(),
      phone: _phoneCtrl.text.trim(),
      profileImage: imageUrl,
    );
    if (!mounted) return;
    if (!success) {
      _showError('Could not save your details');
      return;
    }

    setState(() {
      _saving = false;
      _pickedBytes = null;
      _uploadedUrl = null;
    });
    await showAuthSuccessDialog(
      context,
      title: 'Successful',
      message: 'Your personal details have been saved.',
      actionLabel: 'Back to profile',
      onAction: () => context.canPop() ? context.pop() : context.go('/profile'),
    );
  }

  void _showError(String fallback) {
    if (!mounted) return;
    setState(() => _saving = false);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(ref.read(authProvider).error ?? fallback),
        backgroundColor: AppColors.error,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final user = ref.watch(authProvider).user;
    final savedImage = user?.profileImage;
    final ImageProvider? avatarImage = _pickedBytes != null
        ? MemoryImage(_pickedBytes!)
        : (savedImage != null && savedImage.isNotEmpty ? CachedNetworkImageProvider(savedImage) : null);
    final name = _nameCtrl.text.trim();

    return AuthStepScaffold(
      title: 'Personal Details',
      buttonLabel: 'Save',
      isLoading: _saving,
      onPressed: _save,
      fallbackRoute: '/profile',
      child: Form(
        key: _formKey,
        autovalidateMode: _autovalidate,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const AuthStepMessage(
              'Your personal information will help our\nservice to reach you easily',
            ),
            const SizedBox(height: 28),

            // ─── Avatar with camera badge ───
            Center(
              child: GestureDetector(
                onTap: _saving ? null : _choosePhoto,
                child: Stack(
                  children: [
                    CircleAvatar(
                      radius: 52,
                      backgroundColor: AppColors.primaryLight.withValues(alpha: 0.25),
                      backgroundImage: avatarImage,
                      child: avatarImage == null
                          ? (name.isNotEmpty
                              ? Text(
                                  name[0].toUpperCase(),
                                  style: const TextStyle(
                                    fontSize: 36,
                                    fontWeight: FontWeight.w700,
                                    color: AppColors.primary,
                                  ),
                                )
                              : const Icon(Icons.person_rounded, size: 52, color: AppColors.primary))
                          : null,
                    ),
                    Positioned(
                      right: 0,
                      bottom: 0,
                      child: Container(
                        width: 34,
                        height: 34,
                        decoration: BoxDecoration(
                          gradient: AppColors.primaryGradient,
                          shape: BoxShape.circle,
                          border: Border.all(color: Colors.white, width: 3),
                        ),
                        child: const Icon(Icons.photo_camera_rounded, size: 16, color: Colors.white),
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 32),

            AuthTextField(
              controller: _nameCtrl,
              hint: 'Full name',
              icon: Icons.person_outline_rounded,
              keyboardType: TextInputType.name,
              textInputAction: TextInputAction.next,
              autofillHints: const [AutofillHints.name],
              validator: AuthValidators.fullName,
            ),
            const SizedBox(height: 12),
            AuthTextField(
              controller: _phoneCtrl,
              hint: 'Phone number (optional)',
              icon: Icons.phone_outlined,
              keyboardType: TextInputType.phone,
              textInputAction: TextInputAction.done,
              autofillHints: const [AutofillHints.telephoneNumber],
              validator: AuthValidators.phone,
            ),
            if (user != null) ...[
              const SizedBox(height: 16),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(Icons.email_outlined, size: 16, color: AppColors.textHint),
                  const SizedBox(width: 6),
                  Flexible(
                    child: Text(
                      user.email,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 13, color: AppColors.textSecondary),
                    ),
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }
}
