import 'package:flutter/material.dart';
import 'package:get/get.dart';
import '../controllers/employee_controller.dart';
import '../data/models/employee_model.dart';
import '../data/repositories/employee_repository.dart';

/// Employee List Screen - បង្ហាញបញ្ជីបុគ្គលិក, ស្វែងរក, លុប, និងកែប្រែ
class EmployeeListScreen extends StatefulWidget {
  const EmployeeListScreen({super.key});

  @override
  State<EmployeeListScreen> createState() => _EmployeeListScreenState();
}

class _EmployeeListScreenState extends State<EmployeeListScreen> {
  late final EmployeeController controller;
  final TextEditingController searchController = TextEditingController();
  final RxString searchQuery = ''.obs;

  @override
  void initState() {
    super.initState();
    if (!Get.isRegistered<EmployeeController>()) {
      controller = Get.put(EmployeeController(Get.find<EmployeeRepository>()));
    } else {
      controller = Get.find<EmployeeController>();
    }
  }

  @override
  void dispose() {
    searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        title: const Text(
          'បញ្ជីបុគ្គលិក (Employees)',
          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18),
        ),
        backgroundColor: const Color(0xFF4F46E5),
        foregroundColor: Colors.white,
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh_rounded),
            tooltip: 'ផ្ទុកទិន្នន័យឡើងវិញ',
            onPressed: () => controller.fetchEmployees(),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () {
          controller.clearForm();
          Get.toNamed('/employee/add');
        },
        backgroundColor: const Color(0xFF4F46E5),
        foregroundColor: Colors.white,
        icon: const Icon(Icons.person_add_rounded),
        label: const Text(
          'បន្ថែមបុគ្គលិក',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
      ),
      body: Column(
        children: [
          // === Search Header Bar ===
          Container(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
            decoration: const BoxDecoration(
              color: Color(0xFF4F46E5),
              borderRadius: BorderRadius.only(
                bottomLeft: Radius.circular(20),
                bottomRight: Radius.circular(20),
              ),
            ),
            child: TextField(
              controller: searchController,
              onChanged: (val) => searchQuery.value = val,
              style: const TextStyle(color: Colors.white),
              decoration: InputDecoration(
                hintText: 'ស្វែងរកតាម ឈ្មោះ, មុខតំណែង, ឬ ផ្នែក...',
                hintStyle: TextStyle(color: Colors.white.withOpacity(0.7), fontSize: 13),
                prefixIcon: const Icon(Icons.search_rounded, color: Colors.white70, size: 20),
                suffixIcon: Obx(() {
                  if (searchQuery.value.isNotEmpty) {
                    return IconButton(
                      icon: const Icon(Icons.clear_rounded, color: Colors.white, size: 18),
                      onPressed: () {
                        searchController.clear();
                        searchQuery.value = '';
                      },
                    );
                  }
                  return const SizedBox.shrink();
                }),
                filled: true,
                fillColor: Colors.white.withOpacity(0.18),
                contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: BorderSide.none,
                ),
              ),
            ),
          ),

          // === Employee Counter & Filter Bar ===
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 14, 16, 6),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Obx(() {
                  final filteredCount = _getFilteredEmployees().length;
                  return Text(
                    'បុគ្គលិកសរុប: $filteredCount នាក់',
                    style: const TextStyle(
                      fontWeight: FontWeight.bold,
                      fontSize: 14,
                      color: Color(0xFF334155),
                    ),
                  );
                }),
                Text(
                  'REST API & Repository Pattern',
                  style: TextStyle(
                    fontSize: 11,
                    color: Colors.grey.shade500,
                  ),
                ),
              ],
            ),
          ),

          // === Employee List / Loading / Error State ===
          Expanded(
            child: Obx(() {
              // 1. Loading State
              if (controller.isLoading.value && controller.employees.isEmpty) {
                return const Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      CircularProgressIndicator(color: Color(0xFF4F46E5)),
                      SizedBox(height: 14),
                      Text(
                        'កំពុងផ្ទុកទិន្នន័យបុគ្គលិក...',
                        style: TextStyle(color: Color(0xFF64748B), fontSize: 13),
                      ),
                    ],
                  ),
                );
              }

              // 2. Error State
              if (controller.errorMessage.value.isNotEmpty && controller.employees.isEmpty) {
                return Center(
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        const Icon(Icons.error_outline_rounded, color: Color(0xFFEF4444), size: 48),
                        const SizedBox(height: 12),
                        Text(
                          controller.errorMessage.value,
                          textAlign: TextAlign.center,
                          style: const TextStyle(color: Color(0xFF334155), fontSize: 14),
                        ),
                        const SizedBox(height: 16),
                        ElevatedButton.icon(
                          onPressed: () => controller.fetchEmployees(),
                          icon: const Icon(Icons.refresh, size: 18),
                          label: const Text('ព្យាយាមម្តងទៀត'),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: const Color(0xFF4F46E5),
                            foregroundColor: Colors.white,
                          ),
                        ),
                      ],
                    ),
                  ),
                );
              }

              final filteredList = _getFilteredEmployees();

              // 3. Empty State
              if (filteredList.isEmpty) {
                return Center(
                  child: Padding(
                    padding: const EdgeInsets.all(32),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(Icons.person_search_rounded, size: 64, color: Colors.grey.shade300),
                        const SizedBox(height: 14),
                        Text(
                          searchQuery.value.isNotEmpty
                              ? 'រកមិនឃើញបុគ្គលិកដែលមានឈ្មោះ "${searchQuery.value}" ទេ'
                              : 'មិនទាន់មានទិន្នន័យបុគ្គលិកនៅឡើយទេ',
                          textAlign: TextAlign.center,
                          style: const TextStyle(
                            fontSize: 14,
                            color: Color(0xFF64748B),
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                        const SizedBox(height: 16),
                        if (searchQuery.value.isEmpty)
                          ElevatedButton.icon(
                            onPressed: () {
                              controller.clearForm();
                              Get.toNamed('/employee/add');
                            },
                            icon: const Icon(Icons.add, size: 18),
                            label: const Text('បន្ថែមបុគ្គលិកថ្មី'),
                            style: ElevatedButton.styleFrom(
                              backgroundColor: const Color(0xFF4F46E5),
                              foregroundColor: Colors.white,
                            ),
                          ),
                      ],
                    ),
                  ),
                );
              }

              // 4. Success State - Employee List
              return RefreshIndicator(
                color: const Color(0xFF4F46E5),
                onRefresh: () => controller.fetchEmployees(),
                child: ListView.separated(
                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 90),
                  itemCount: filteredList.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 12),
                  itemBuilder: (context, index) {
                    final employee = filteredList[index];
                    return _buildEmployeeCard(employee);
                  },
                ),
              );
            }),
          ),
        ],
      ),
    );
  }

  List<EmployeeModel> _getFilteredEmployees() {
    final query = searchQuery.value.trim().toLowerCase();
    if (query.isEmpty) {
      return controller.employees;
    }
    return controller.employees.where((emp) {
      final nameMatches = emp.name.toLowerCase().contains(query);
      final posMatches = emp.position.toLowerCase().contains(query);
      final deptMatches = emp.department.toLowerCase().contains(query);
      final emailMatches = emp.email?.toLowerCase().contains(query) ?? false;
      return nameMatches || posMatches || deptMatches || emailMatches;
    }).toList();
  }

  Widget _buildEmployeeCard(EmployeeModel employee) {
    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.grey.shade100),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.03),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Avatar with Department Color
                CircleAvatar(
                  radius: 26,
                  backgroundColor: _getDepartmentColor(employee.department).withOpacity(0.12),
                  child: Text(
                    employee.name.isNotEmpty ? employee.name[0] : '?',
                    style: TextStyle(
                      fontSize: 20,
                      fontWeight: FontWeight.bold,
                      color: _getDepartmentColor(employee.department),
                    ),
                  ),
                ),
                const SizedBox(width: 14),

                // Employee Basic Details
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Flexible(
                            child: Text(
                              employee.name,
                              style: const TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.bold,
                                color: Color(0xFF1E293B),
                              ),
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                            decoration: BoxDecoration(
                              color: _getDepartmentColor(employee.department).withOpacity(0.1),
                              borderRadius: BorderRadius.circular(8),
                            ),
                            child: Text(
                              employee.department,
                              style: TextStyle(
                                fontSize: 11,
                                fontWeight: FontWeight.bold,
                                color: _getDepartmentColor(employee.department),
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 4),
                      Text(
                        employee.position,
                        style: const TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w600,
                          color: Color(0xFF475569),
                        ),
                      ),
                      const SizedBox(height: 6),
                      Row(
                        children: [
                          const Icon(Icons.attach_money_rounded, size: 16, color: Color(0xFF10B981)),
                          Text(
                            'ប្រាក់ខែ: \$${employee.salary.toStringAsFixed(0)}',
                            style: const TextStyle(
                              fontSize: 12.5,
                              fontWeight: FontWeight.w600,
                              color: Color(0xFF10B981),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ],
            ),

            const Divider(height: 20, thickness: 0.8),

            // Contact info and Action buttons
            Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      if (employee.phone != null && employee.phone!.isNotEmpty)
                        Row(
                          children: [
                            const Icon(Icons.phone_outlined, size: 14, color: Color(0xFF64748B)),
                            const SizedBox(width: 6),
                            Text(
                              employee.phone!,
                              style: const TextStyle(fontSize: 11.5, color: Color(0xFF64748B)),
                            ),
                          ],
                        ),
                      if (employee.email != null && employee.email!.isNotEmpty)
                        Padding(
                          padding: const EdgeInsets.only(top: 2),
                          child: Row(
                            children: [
                              const Icon(Icons.email_outlined, size: 14, color: Color(0xFF64748B)),
                              const SizedBox(width: 6),
                              Flexible(
                                child: Text(
                                  employee.email!,
                                  style: const TextStyle(fontSize: 11.5, color: Color(0xFF64748B)),
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                            ],
                          ),
                        ),
                    ],
                  ),
                ),

                // Edit Button
                IconButton(
                  icon: const Icon(Icons.edit_note_rounded, color: Color(0xFF4F46E5), size: 24),
                  tooltip: 'កែប្រែព័ត៌មាន (Edit)',
                  onPressed: () {
                    controller.fillFormForEdit(employee);
                    Get.toNamed('/employee/edit');
                  },
                ),

                // Delete Button with Dialog Confirmation
                IconButton(
                  icon: const Icon(Icons.delete_outline_rounded, color: Color(0xFFEF4444), size: 22),
                  tooltip: 'លុបបុគ្គលិក (Delete)',
                  onPressed: () {
                    _showDeleteConfirmation(employee);
                  },
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  void _showDeleteConfirmation(EmployeeModel employee) {
    Get.defaultDialog(
      title: 'បញ្ជាក់ការលុបបុគ្គលិក',
      titleStyle: const TextStyle(
        fontWeight: FontWeight.bold,
        fontSize: 16,
        color: Color(0xFF1E293B),
      ),
      content: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8),
        child: Column(
          children: [
            const Icon(Icons.warning_amber_rounded, color: Color(0xFFEF4444), size: 48),
            const SizedBox(height: 12),
            Text(
              'តើអ្នកប្រាកដថាចង់លុបបុគ្គលិក "${employee.name}" មែនទេ?',
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 14, color: Color(0xFF334155)),
            ),
            const SizedBox(height: 6),
            const Text(
              'សកម្មភាពនេះមិនអាចត្រឡប់វិញបានឡើយ។',
              style: TextStyle(fontSize: 12, color: Color(0xFF94A3B8)),
            ),
          ],
        ),
      ),
      textConfirm: 'លុបចេញ',
      textCancel: 'បោះបង់',
      confirmTextColor: Colors.white,
      buttonColor: const Color(0xFFEF4444),
      cancelTextColor: const Color(0xFF64748B),
      radius: 16,
      onConfirm: () {
        Get.back();
        if (employee.id != null) {
          controller.deleteEmployee(employee.id!);
        }
      },
    );
  }

  Color _getDepartmentColor(String dept) {
    switch (dept.toUpperCase()) {
      case 'IT':
        return const Color(0xFF4F46E5);
      case 'DESIGN':
        return const Color(0xFFEC4899);
      case 'HR':
        return const Color(0xFF8B5CF6);
      case 'MARKETING':
        return const Color(0xFFF59E0B);
      case 'FINANCE':
        return const Color(0xFF10B981);
      default:
        return const Color(0xFF0EA5E9);
    }
  }
}

