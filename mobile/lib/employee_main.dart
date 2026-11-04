import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';

import 'app/bindings/app_binding.dart';
import 'app/routes/app_pages.dart';

/// Entry point សម្រាប់ដំណើរការប្រព័ន្ធគ្រប់គ្រងបុគ្គលិក (GetX Employee Management)
/// អាចដំណើរការដោយប្រើពាក្យបញ្ជា៖
/// flutter run -t lib/employee_main.dart
void main() {
  WidgetsFlutterBinding.ensureInitialized();

  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.light,
    ),
  );

  runApp(const EmployeeManagementApp());
}

class EmployeeManagementApp extends StatelessWidget {
  const EmployeeManagementApp({super.key});

  @override
  Widget build(BuildContext context) {
    return GetMaterialApp(
      title: 'ប្រព័ន្ធគ្រប់គ្រងបុគ្គលិក (Employee Management)',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF4F46E5),
          primary: const Color(0xFF4F46E5),
        ),
        useMaterial3: true,
        scaffoldBackgroundColor: const Color(0xFFF8FAFC),
        appBarTheme: const AppBarTheme(
          centerTitle: false,
          elevation: 0,
        ),
      ),
      initialBinding: AppBinding(),
      initialRoute: AppPages.initial,
      getPages: AppPages.routes,
      defaultTransition: Transition.cupertino,
    );
  }
}

