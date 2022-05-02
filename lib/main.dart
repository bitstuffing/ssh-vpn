import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'package:flutter/material.dart';
import 'package:fluttertoast/fluttertoast.dart';

import 'utils/utils.dart';
import 'widgets/drawer.dart';

void main() => runApp(MainApp());

/**
 * Main app
 */
class MainApp extends StatelessWidget {
  const MainApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter Demo',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        primarySwatch: Colors.blue,
      ),
      home: const HomePage(),
    );
  }
}

class HomePage extends StatefulWidget {
  const HomePage( { Key? key } ) : super(key: key);
  @override
  HomePageState createState() => HomePageState();
}

/**
 * Main widgets with buttons by places
 */
class HomePageState extends State<HomePage> {
  @override
  Widget build(BuildContext context) {
    Fluttertoast.showToast(
        msg: "Welcome",
        toastLength: Toast.LENGTH_LONG,
        gravity: ToastGravity.BOTTOM,
        timeInSecForIosWeb: 1,
        backgroundColor: Colors.blueGrey,
        textColor: Colors.black,
        fontSize: 16.0
    );
    startService();
    return Scaffold(
        backgroundColor: bgColor,
        appBar: AppBar(title: const Text("VPN throw SSH")),
        drawer: NavDrawer(),
        body: Body(context),
    );
  }
}
/**
 * Body
 */
Widget Body(BuildContext context){
  double screenWidth = MediaQuery.of(context).size.width;
  return ListView(
    children: [
      Stack(
        alignment: Alignment.topCenter,
        overflow: Overflow.visible,
        children: <Widget>[
          upperCurvedContainer(context),
          circularButtonWidget(context, screenWidth),
        ],
      ),
      SizedBox(height: screenWidth * 0.70),
      connectedStatusText(),
    ],
  );
}

Widget upperCurvedContainer(BuildContext context) {
  return ClipPath(
    clipper: Clipper(),
    child: Container(
      padding: const EdgeInsets.symmetric(horizontal: 25, vertical: 60),
      height: 240,
      width: MediaQuery.of(context).size.width,
      decoration: const BoxDecoration(
        gradient: curveGradient,
      ),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        crossAxisAlignment: CrossAxisAlignment.center,
        children: <Widget>[
          titleRow(),
          bottomRow(),
        ],
      ),
    ),
  );
}

Widget titleRow(){
  return const Text('VPN', style: vpnStyle);
}

Widget bottomRow() {
  return Row(
    mainAxisAlignment: MainAxisAlignment.spaceBetween,
    children: const <Widget>[
      Text(
        'Upload:\n\n200 KB/s',
        style: txtSpeedStyle,
      ),
      Text(
        'Download:\n\n10 MB/s',
        style: txtSpeedStyle,
      ),
    ],
  );
}

Widget circularButtonWidget(BuildContext context, width) {
  return Positioned(
    bottom: -width * 0.55,
    child: Stack(
      alignment: Alignment.center,
      children: <Widget>[
        Container(
          height: width * 0.50,
          width: width * 0.50,
          decoration: const BoxDecoration(
            shape: BoxShape.circle,
            gradient: curveGradient,
            // color: Colors.red,
          ),
          child: Center(
            child: Container(
              height: width * 0.4,
              width: width * 0.4,
              decoration: const BoxDecoration(
                shape: BoxShape.circle,
                color: bgColor,
              ),
              child: Center(
                child: Padding(
                  padding: EdgeInsets.all(30),
                  /*decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      gradient: greenGradient,
                      boxShadow: [
                        BoxShadow(
                          color: Color(0XFF00D58D).withOpacity(.2),
                          spreadRadius: 15,
                          blurRadius: 15,
                        ),
                      ]),*/

                  child: SizedBox(
                    width: double.infinity, // <-- match_parent
                    height: double.infinity, // <-- match-parent
                    child: ElevatedButton(
                      onPressed: () {

                        Fluttertoast.showToast(
                            msg: "Starting VPN service...",
                            toastLength: Toast.LENGTH_SHORT,
                            gravity: ToastGravity.CENTER,
                            timeInSecForIosWeb: 1,
                            backgroundColor: Colors.red,
                            textColor: Colors.white,
                            fontSize: 16.0
                        );



                      },
                      style: ElevatedButton.styleFrom(
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(55),
                          )
                      ),
                      child: const Icon(Icons.wifi_rounded, color: Colors.white, size: 70),
                    )
                  ),
                ),
              ),
            ),
          ),
        ),

        //top left widget
        Positioned(
          left: 5,
          top: 5,
          child: Container(
            padding: EdgeInsets.all(8),
            height: 60,
            width: 60,
            decoration: BoxDecoration(color: bgColor, shape: BoxShape.circle),
            child: const Center(
              child: CircleAvatar(
                backgroundImage: NetworkImage(lockUrl),
                radius: 40,
                backgroundColor: Colors.transparent,
              ),
            ),
          ),
        ),
      ],
    ),
  );
}

void startService() async{
  if(Platform.isAndroid) {
    var methodChannel = MethodChannel("com.github.bitstuffing.sshvpn");
    String data = await methodChannel.invokeMethod("startService");
    print("data: $data");
  }
}

Widget connectedStatusText() {
  return Align(
    alignment: Alignment.center,
    child: RichText(
      textAlign: TextAlign.center,
      text: const TextSpan(text: 'Status :', style: connectedStyle, children: [
        TextSpan(text: ' connected\n', style: connectedGreenStyle),
        TextSpan(text: '01:02:03', style: connectedSubtitle),
      ]),
    ),
  );
}
