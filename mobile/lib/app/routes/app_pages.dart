import 'package:get/get.dart';
import '../views/employee_list_screen.dart';
import '../views/employee_form_screen.dart';

/// App Pages - កំណត់ Routes និង Navigation តាម GetX
class AppPages {
  // ចាប់ផ្តើមដោយផ្ទាល់នៅទំព័របញ្ជីបុគ្គលិក (Employee List)
  static const initial = '/';

  static final routes = [
    GetPage(
      name: '/',
      page: () => const EmployeeListScreen(),
    ),
    GetPage(
      name: '/employee/add',
      page: () => const EmployeeFormScreen(),
      transition: Transition.rightToLeft,
    ),
    GetPage(
      name: '/employee/edit',
      page: () => const EmployeeFormScreen(),
      transition: Transition.rightToLeft,
    ),
  ];
}
