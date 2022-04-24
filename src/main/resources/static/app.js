var stompClient = null;
var sessionId = null;

var connected = false;
var disconnectionTimeout = null;
var sendButtonState = 'execute'; // 'execute' or 'stop'

var theme = 'light';

$(window).on('load', () => {
    window.editor.layout();
});
$(window).on('resize', () => window.editor.layout());

function setConnected(isConnected) {
    $("#connect").prop("disabled", isConnected);
    $("#disconnect").prop("disabled", !isConnected);
    connected = isConnected;

    if (!connected) {
        makeExecuteBtn();
    }
    $("#output").html("");
}

function connect() {
    var socket = new SockJS('/programrun');
    stompClient = Stomp.over(socket);
    sessionId = Math.floor(Math.random() * 1000000);

    stompClient.connect({}, function (frame) {
        setConnected(true);
        console.log('Connected: ' + frame);

        stompClient.subscribe('/topic/run/' + sessionId, function (message) {
            var outputMessage = JSON.parse(message.body);

            if (outputMessage.containsError) {
                showError(outputMessage.error.replaceAll("\r\n", "<br/>"));
            }
            else {
                showOutput(outputMessage.output.replaceAll("\r\n", "<br/>"));
            }
        });
        stompClient.subscribe('/topic/finished/' + sessionId, function (message) {
            var finishedMsg = JSON.parse(message.body);
            setTimeout(() => {
                console.log(finishedMsg);
                if (!finishedMsg.finished) {
                    return;
                }
                showOutput(finishedMsg.finishText);
                makeExecuteBtn();
            }, 800);
        });
    });
}

function disconnect() {
    if (stompClient !== null) {
        stompClient.disconnect();
    }
    setConnected(false);
    sessionId = null;
    console.log("Disconnected");
}

function makeStopBtn() {
    $('#send').removeClass('btn-success').addClass('btn-danger');
    // <!--! Font Awesome Pro 6.1.1 by @fontawesome - https://fontawesome.com License - https://fontawesome.com/license (Commercial License) Copyright 2022 Fonticons, Inc. -->
    $('#send').html('<svg xmlns="http://www.w3.org/2000/svg" style="height:1.5rem; width:1.5rem;" fill="white" viewBox="0 0 384 512"><path d="M384 128v255.1c0 35.35-28.65 64-64 64H64c-35.35 0-64-28.65-64-64V128c0-35.35 28.65-64 64-64H320C355.3 64 384 92.65 384 128z"/></svg>')
    sendButtonState = 'stop';
}

function makeExecuteBtn() {
    $('#send').removeClass('btn-danger').addClass('btn-success');
    // <!--! Font Awesome Pro 6.1.1 by @fontawesome - https://fontawesome.com License - https://fontawesome.com/license (Commercial License) Copyright 2022 Fonticons, Inc. -->
    $('#send').html('<svg xmlns="http://www.w3.org/2000/svg" style="height:1.5rem; width:1.5rem;" fill="white" viewBox="0 0 384 512"><path d="M361 215C375.3 223.8 384 239.3 384 256C384 272.7 375.3 288.2 361 296.1L73.03 472.1C58.21 482 39.66 482.4 24.52 473.9C9.377 465.4 0 449.4 0 432V80C0 62.64 9.377 46.63 24.52 38.13C39.66 29.64 58.21 29.99 73.03 39.04L361 215z"/></svg>')
    sendButtonState = 'execute';
}

function sendProgram() {
    var doSend = () => {
        // disconnect after 180 seconds automatically
        disconnectionTimeout = setTimeout(() => {
             disconnect();
         }, 120000);
        var programOrInputJson = sendButtonState == 'execute' ? JSON.stringify(
                                          {
                                              'program': window.editor.getValue(),
                                              'hasInput': false,
                                              'input': "",
                                              'cmdlineArgs': "",
                                              'endRun': false
                                          }
                                      ) : JSON.stringify(
                                        {
                                            'program': '',
                                            'hasInput': false,
                                            'cmdlineArgs': "",
                                            'input': "",
                                            'endRun': true
                                        }
                                      );
         console.log(programOrInputJson);
         stompClient.send("/app/run/" + sessionId, {}, programOrInputJson);
         makeStopBtn();
    };
    if (!connected) {
        connect();

        // wait for some time to get connected
        setTimeout(doSend, 1200);
    }
    else if (connected && disconnectionTimeout != null) {
        // restart disconnection timeout if execution was triggered before timeout
        clearTimeout(disconnectionTimeout);
        doSend();
    }
}

function sendInput(message) {
    $("#output").append("<tr><td><i>" + $("#programInput").val() + "</i></td></tr>");
    stompClient.send("/app/run/" + sessionId, {}, JSON.stringify(
        {
            'program': '',
            'hasInput': true,
            'input': $("#programInput").val(),
            'cmdlineArgs': ""
        }
    ));
}

function showOutput(message) {
    setTimeout(() => $("#output").append('<tr><td style="font-family:monospace">' + message + "</td></tr>"), 300);
}

function showError(message) {
    setTimeout(() => $("#output").append('<tr><td><span style="font-family:monospace; color:red">' + message + "</span></td></tr>"), 300);
}

$(function () {
    $("form").on('submit', function (e) {
        e.preventDefault();
    });
    $( "#connect" ).click(function() { connect(); });
    $( "#disconnect" ).click(function() { disconnect(); });
    $( "#send" ).click(function() {
        if (sendButtonState == 'execute') {
            $("#output").empty();
        }
        sendProgram();
    });
    $( "#sendInput" ).click(function() { sendInput(); });
    $( "#clearOutput" ).click(function() { $("#output").empty(); })

    // Dark/light switch:
    $( "#darkModeBtn" ).click(function() {
        theme = theme == 'light' ? 'dark' : 'light';
        $("#bodyElem").toggleClass('dark');
        monaco.editor.setTheme(theme == 'light' ? 'vs' : 'vs-dark');
        $("#darkModeBtn").html(theme == 'light' ? 'Dark theme' : 'Light theme');
        $("#monacoContainer").toggleClass('monacoEditorDark');
        $("#programInput").toggleClass('bg-dark');
        $("#programInput").toggleClass('text-white');
        $("#programInput").toggleClass('border-0');
    })
});