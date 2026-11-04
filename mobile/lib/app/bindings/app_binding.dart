import 'package:get/get.dart';
import '../data/providers/employee_provider.dart';
import '../data/repositories/employee_repository.dart';
import '../controllers/employee_controller.dart';

/// App Binding - ចុះឈ្មោះ Dependencies សម្រាប់ប្រព័ន្ធគ្រប់គ្រងបុគ្គលិក
/// អនុវត្ត Dependency Injection តាម GetX
class AppBinding extends Bindings {
  @override
  void dependencies() {
    // 1. Data Provider (API / Data Source)
    Get.lazyPut<EmployeeProvider>(() => EmployeeProvider(), fenix: true);

    // 2. Repository Pattern (ស្រទាប់កណ្តាលរវាង Provider និង Controller)
    Get.lazyPut<EmployeeRepository>(
      () => EmployeeRepository(Get.find<EmployeeProvider>()),
      fenix: true,
    );

    // 3. GetX Controller (គ្រប់គ្រង State និង Business Logic)
    Get.lazyPut<EmployeeController>(
      () => EmployeeController(Get.find<EmployeeRepository>()),
      fenix: true,
    );
  }
}
