import 'package:flutter/material.dart';
import 'package:settings_ui/settings_ui.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({Key? key}) : super(key: key);
  @override
  SettingsScreenState createState() => SettingsScreenState();
}

class SettingsScreenState extends State<SettingsScreen> {
  bool notificationsEnabled = true;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
          elevation: 0.0,
          backgroundColor: Color(0xff0000ff),
          title: const Text('Settings')
      ),
      body: getSettingsList(),
    );
  }

  Widget getSettingsList() {
    /*return CustomSection(
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Padding(
                  padding: EdgeInsetsDirectional.only(start: 15, top: 15, bottom: 15),
                  child: Text('SSH'),
                ),
                Card(
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(8),
                  ),
                  margin: EdgeInsets.symmetric(horizontal: 8),
                  child: Padding(
                    padding: EdgeInsets.symmetric(vertical: 6),
                    child: Column(
                      children: [
                        TextFormField(
                            obscureText: false,
                            decoration: InputDecoration(labelText: 'Host'),
                            validator: (value) {
                              if (value == null || value.isEmpty) {
                                return 'Hostname/IP is required';
                              }
                            })
                        ,TextFormField(
                            obscureText: false,
                            decoration: InputDecoration(labelText: 'Username'),
                            validator: (value) {
                              if (value == null || value.isEmpty) {
                                return 'Username is required';
                              }
                            })
                        ,TextFormField(
                          obscureText: true,
                          decoration: InputDecoration(labelText: 'Password'),
                          validator: (value) {
                            if (value == null || value.isEmpty) {
                              return 'Password is required';
                            }
                          })
                      ],
                    ),
                  ),
                ),
                const Padding(
                  padding: EdgeInsetsDirectional.only(start: 14, top: 15, bottom: 15),
                  child: Text('Configuration'),
                ),
                Card(
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(8),
                  ),
                  margin: EdgeInsets.symmetric(horizontal: 8),
                  child: Padding(
                    padding: EdgeInsets.symmetric(vertical: 6),
                    child: Column(
                      children: [

                        SettingsTile.switchTile(
                          title: 'Enable Notifications',
                          enabled: notificationsEnabled,
                          leading: Icon(Icons.notifications_active),
                          switchValue: true,
                          onToggle: (value) {},
                        ),
                        Divider(height: 0, thickness: 0.5, indent: 50),

                      ],
                    ),
                  ),
                )
              ],
            ),
          ),
        );
     */
    return MaterialApp(
      home: DefaultTabController(
        length: 2,
        child: Scaffold(
            appBar: getTabBar(),
            body: getTabBarView()
        ),
      ),
    );
  }

  /**
   * getTabBar(),
      getTabBarView()
   */

  List<Flexible> getSSHWidgets() {
    return [
    Flexible(
        child: TextFormField(
          obscureText: false,
          decoration: const InputDecoration(labelText: 'Host/IP'),
          validator: (value) {
            if (value == null || value.isEmpty) {
              return 'Hostname/IP is required';
            }
          })
    ),
    Flexible(child:TextFormField(
        obscureText: false,
        decoration: const InputDecoration(labelText: 'Username'),
        validator: (value) {
          if (value == null || value.isEmpty) {
            return 'Username is required';
          }
        })
    ),Flexible(child:TextFormField(
        obscureText: true,
        decoration: const InputDecoration(labelText: 'Password'),
        validator: (value) {
          if (value == null || value.isEmpty) {
            return 'Password is required';
          }
        })
      )
    ];
  }

  Widget getSSHTab() {
    return Card(
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(8),
      ),
      margin: const EdgeInsets.symmetric(horizontal: 8),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 6),
        child: Column(
          children: getSSHWidgets(),
        ),
      ),
    );
  }

  Widget getConfigurationTab() {
    return Card(
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(8),
      ),
      margin: const EdgeInsets.symmetric(horizontal: 8),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 6),
        child: Column(
          children: [

            SettingsTile.switchTile(
              title: 'Enable Notifications',
              leading: const Icon(Icons.notifications_active),
              switchValue: notificationsEnabled,
              onToggle: (bool value) {
                setState(() {
                  notificationsEnabled = value;
                });
              },
            ),
            const Divider(height: 0, thickness: 0.5, indent: 50),

          ],
        ),
      ),

    );
  }

  TabBarView getTabBarView() {
    return TabBarView(
      children: [
        getConfigurationTab(),
        getSSHTab()
      ]
    );
  }

  TabBar getTabBar() {
    return TabBar(
      automaticIndicatorColorAdjustment: true,
        labelColor: Color.fromRGBO(0, 0, 0, 1),
        tabs: <Widget>[
          Tab(
            text: 'Configuration',
            icon: Icon(Icons.settings),
          ),
          Tab(
            text: 'SSH',
            icon: Icon(Icons.computer),
          )
        ],
      );
  }
}